package org.crux.ui.screen.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A third-party library that ships inside Crux, as listed by the AboutLibraries Gradle plugin. */
internal data class OpenSourceLibrary(
    val id: String,
    val name: String,
    val version: String,
    /** Display names of the licenses; empty when the library does not declare one. */
    val licenses: List<String>,
    val website: String?,
)

/**
 * Licenses for libraries whose published POM declares none, checked against the upstream repository
 * (GitHub reports Tlaster/Swiper as MIT) and used only when the generated data lists no license.
 */
internal val UNDECLARED_LICENSE_OVERRIDES: Map<String, String> =
    mapOf("com.github.tlaster:swiper" to "MIT License")

/**
 * Reads the `aboutlibraries.json` generated at build time (res/raw). Unknown or malformed entries
 * are skipped instead of failing the whole screen.
 */
internal fun parseOpenSourceLibraries(json: String): List<OpenSourceLibrary> {
    val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return emptyList()
    val licenseNames =
        (root["licenses"] as? JsonObject)
            .orEmpty()
            .mapValues { (key, value) ->
                (value as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull ?: key
            }
    val libraries = (root["libraries"] as? JsonArray).orEmpty()
    return libraries
        .mapNotNull { element ->
            val library = element as? JsonObject ?: return@mapNotNull null
            val id = library["uniqueId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name =
                library["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != id }
                    ?: id.substringAfter(':')
            val licenses =
                (library["licenses"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it.jsonPrimitive.contentOrNull }
                    .map { licenseNames[it] ?: it }
                    .ifEmpty { listOfNotNull(UNDECLARED_LICENSE_OVERRIDES[id.lowercase()]) }
            val website =
                library["website"]?.jsonPrimitive?.contentOrNull
                    ?: (library["scm"] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            OpenSourceLibrary(
                id = id,
                name = name,
                version = library["artifactVersion"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                licenses = licenses,
                website = website?.trim()?.takeIf { it.startsWith("https://") },
            )
        }.distinctBy { it.id }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
}

private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this?.jsonArray ?: emptyList()
