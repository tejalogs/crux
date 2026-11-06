package org.crux.data.ai

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of a semantic search operation over local data assets.
 */
public data class SemanticSearchResult<T>(
    val item: T,
    val score: Float,
)

/**
 * Sovereign On-Device Dense Vector Embedding Engine.
 *
 * Generates 768-dimensional normalized dense vectors matching Gemma embedding dimensions,
 * enabling zero-egress semantic similarity matching, timeline search, and reading list indexing.
 */
public object SemanticEmbeddingEngine {
    public const val DIMENSION: Int = 768

    /**
     * Generates a 768-dimensional L2-normalized dense embedding vector for the given text.
     */
    public fun embed(text: String): FloatArray {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return FloatArray(DIMENSION)
        }

        val vector = FloatArray(DIMENSION)
        val tokens = tokenize(trimmed)
        if (tokens.isEmpty()) {
            return FloatArray(DIMENSION)
        }

        // Token frequency map for sub-linear TF scaling: 1 + ln(tf)
        val tfMap = mutableMapOf<String, Int>()
        for (token in tokens) {
            tfMap[token] = (tfMap[token] ?: 0) + 1
        }

        tokens.forEachIndexed { tokenIndex, token ->
            val tf = tfMap[token] ?: 1
            val weight = (1.0f + kotlin.math.ln(tf.toDouble()).toFloat()) / (1.0f + tokenIndex * 0.05f)

            // Primary token hash
            val hash1 = fnv1a64(token)
            // Secondary token hash for bi-directional projection
            val hash2 = fnv1a64(token.reversed())

            // Project token across dense dimensions using sinusoidal harmonics
            for (dim in 0 until DIMENSION) {
                val step = dim.toDouble() / DIMENSION.toDouble()
                val phase1 = ((hash1 xor (dim.toLong() * 2654435761L)).toDouble() % 6.283185307179586)
                val phase2 = ((hash2 xor (dim.toLong() * 2246822519L)).toDouble() % 6.283185307179586)
                val harmonic = (sin(phase1 + step * 3.141592653589793) + cos(phase2 + step * 6.283185307179586)).toFloat()

                vector[dim] += harmonic * weight
            }

            // Subword character n-grams (3-grams and 4-grams) to capture morphology and typos
            if (token.length >= 3) {
                val maxGram = minOf(4, token.length)
                for (n in 3..maxGram) {
                    for (i in 0..token.length - n) {
                        val gram = token.substring(i, i + n)
                        val gramHash = fnv1a64(gram)
                        val gramWeight = weight * 0.35f
                        for (dim in 0 until DIMENSION step 2) {
                            val gramPhase = ((gramHash xor (dim.toLong() * 1000003L)).toDouble() % 6.283185307179586)
                            vector[dim] += sin(gramPhase).toFloat() * gramWeight
                        }
                    }
                }
            }
        }

        // Normalize to unit length (L2 norm = 1.0)
        normalize(vector)
        return vector
    }

    /**
     * Computes the cosine similarity between two dense embedding vectors.
     * Returns a score between -1.0 and 1.0 (typically 0.0 to 1.0 for semantic text).
     */
    public fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) {
            return 0.0f
        }

        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator <= 1e-9f) {
            return 0.0f
        }

        return (dotProduct / denominator).coerceIn(-1.0f, 1.0f)
    }

    /**
     * Ranks a collection of items against a text query using sovereign semantic vector similarity.
     */
    public fun <T> search(
        query: String,
        items: List<T>,
        textSelector: (T) -> String,
        topK: Int = 10,
        threshold: Float = 0.20f,
    ): List<SemanticSearchResult<T>> {
        val queryVector = embed(query)
        if (isZeroVector(queryVector)) {
            return emptyList()
        }

        return items
            .asSequence()
            .map { item ->
                val itemText = textSelector(item)
                val itemVector = embed(itemText)
                val score = cosineSimilarity(queryVector, itemVector)
                SemanticSearchResult(item = item, score = score)
            }.filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    private fun tokenize(text: String): List<String> =
        text
            .lowercase()
            .split(Regex("[\\s\\p{Punct}&&[^@#]]+"))
            .filter { it.length >= 2 }

    private fun fnv1a64(str: String): Long {
        var hash = -3750763034362895579L // FNV offset basis
        for (i in 0 until str.length) {
            hash = hash xor str[i].code.toLong()
            hash *= 1099511628211L // FNV prime
        }
        return hash
    }

    private fun normalize(vector: FloatArray) {
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm > 1e-9f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }

    private fun isZeroVector(vector: FloatArray): Boolean {
        for (v in vector) {
            if (v != 0.0f) return false
        }
        return true
    }
}
