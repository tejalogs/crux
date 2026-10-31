package dev.dimension.flare.data.ai

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticEmbeddingEngineTest {
    @Test
    fun generates768DimensionalNormalizedVector() {
        val vector = SemanticEmbeddingEngine.embed("Flare sovereign multiplatform social client")
        assertEquals(768, vector.size)

        var sumSq = 0.0f
        for (v in vector) {
            sumSq += v * v
        }
        val norm = sqrt(sumSq)
        assertTrue(abs(norm - 1.0f) < 1e-4f, "Embedding vector should have unit L2-norm ~ 1.0 (was $norm)")
    }

    @Test
    fun identicalTextsHavePerfectCosineSimilarity() {
        val text = "Zero-egress local intelligence with Gemma 4 Edge"
        val v1 = SemanticEmbeddingEngine.embed(text)
        val v2 = SemanticEmbeddingEngine.embed(text)

        val similarity = SemanticEmbeddingEngine.cosineSimilarity(v1, v2)
        assertTrue(abs(similarity - 1.0f) < 1e-4f, "Identical embeddings must have cosine similarity 1.0 (was $similarity)")
    }

    @Test
    fun semanticallyRelatedTextsScoreHigherThanUnrelatedTexts() {
        val anchor = SemanticEmbeddingEngine.embed("Mastodon decentralized fediverse social protocol")
        val related = SemanticEmbeddingEngine.embed("Fediverse activitypub decentralized social networks")
        val unrelated = SemanticEmbeddingEngine.embed("Baking chocolate chip cookies with vanilla and flour")

        val scoreRelated = SemanticEmbeddingEngine.cosineSimilarity(anchor, related)
        val scoreUnrelated = SemanticEmbeddingEngine.cosineSimilarity(anchor, unrelated)

        assertTrue(
            scoreRelated > scoreUnrelated,
            "Related text similarity ($scoreRelated) should exceed unrelated text similarity ($scoreUnrelated)",
        )
    }

    @Test
    fun semanticSearchRanksMostRelevantItemsFirst() {
        data class Post(
            val id: Int,
            val content: String,
        )

        val posts =
            listOf(
                Post(1, "Recipe for Italian margherita pizza with fresh basil and mozzarella"),
                Post(2, "Bluesky announces new AT Protocol federation updates for sovereign accounts"),
                Post(3, "Mastodon instance moderation rules and fediverse relay servers"),
                Post(4, "How to tune guitar strings with standard EADGBE acoustic tuning"),
            )

        val results =
            SemanticEmbeddingEngine.search(
                query = "fediverse mastodon social relay",
                items = posts,
                textSelector = { it.content },
                topK = 2,
            )

        assertTrue(results.isNotEmpty(), "Search should find matching items")
        assertEquals(3, results.first().item.id, "Most relevant post should be #3 (Mastodon/Fediverse)")
    }

    @Test
    fun emptyOrWhitespaceQueryReturnsEmptyResults() {
        val posts = listOf("Hello world", "Flare social")
        val results =
            SemanticEmbeddingEngine.search(
                query = "   ",
                items = posts,
                textSelector = { it },
            )
        assertTrue(results.isEmpty())
    }
}
