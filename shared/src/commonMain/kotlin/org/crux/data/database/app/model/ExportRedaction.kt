package org.crux.data.database.app.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val redactionJson = Json { ignoreUnknownKeys = true }

/**
 * Removes web session cookies from an account's stored credential before it goes into an export
 * file. Crux keeps logins in per-site WebView profiles, never in the export, so any field whose
 * name contains "cookie" is blanked. A credential that is not a JSON object is replaced by `{}`.
 */
internal fun redactCredentialForExport(credentialJson: String): String {
    val element =
        try {
            redactionJson.parseToJsonElement(credentialJson)
        } catch (_: Exception) {
            return "{}"
        }
    if (element !is JsonObject) return "{}"
    val (redacted, changed) = redactCookieFields(element)
    return if (changed) redactionJson.encodeToString(JsonElement.serializer(), redacted) else credentialJson
}

private fun redactCookieFields(element: JsonObject): Pair<JsonObject, Boolean> {
    var changed = false
    val fields =
        element.mapValues { (key, value) ->
            when {
                key.contains("cookie", ignoreCase = true) && value !is JsonNull && value != JsonPrimitive("") -> {
                    changed = true
                    JsonPrimitive("")
                }

                value is JsonObject -> {
                    val (nested, nestedChanged) = redactCookieFields(value)
                    changed = changed || nestedChanged
                    nested
                }

                else -> {
                    value
                }
            }
        }
    return JsonObject(fields) to changed
}

internal fun DbAccount.redactedForExport(): DbAccount = copy(credential_json = redactCredentialForExport(credential_json))
