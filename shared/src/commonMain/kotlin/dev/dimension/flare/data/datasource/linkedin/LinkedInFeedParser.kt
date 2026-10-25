@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.dimension.flare.data.datasource.linkedin

import dev.dimension.flare.common.JSON
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

public object LinkedInTextSerializer : KSerializer<LinkedInText> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LinkedInText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LinkedInText {
        val jsonDecoder = decoder as? JsonDecoder ?: return LinkedInText(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> LinkedInText(element.contentOrNull)
            is JsonObject -> {
                val text = element["text"]?.let {
                    (it as? JsonPrimitive)?.contentOrNull
                        ?: (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                } ?: element["value"]?.jsonPrimitive?.contentOrNull
                LinkedInText(text)
            }
            else -> LinkedInText(null)
        }
    }

    override fun serialize(encoder: Encoder, value: LinkedInText) {
        if (value.text != null) {
            encoder.encodeString(value.text)
        } else {
            encoder.encodeNull()
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
public object LinkedInCommentarySerializer : KSerializer<LinkedInCommentary> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("LinkedInCommentary")

    override fun deserialize(decoder: Decoder): LinkedInCommentary {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return LinkedInCommentary(LinkedInText(decoder.decodeString()))
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> LinkedInCommentary(LinkedInText(element.contentOrNull))
            is JsonObject -> {
                val textElement = element["text"]
                val text = when (textElement) {
                    is JsonPrimitive -> LinkedInText(textElement.contentOrNull)
                    is JsonObject -> {
                        val nestedText = textElement["text"]?.let {
                            (it as? JsonPrimitive)?.contentOrNull
                        } ?: textElement["value"]?.jsonPrimitive?.contentOrNull
                        LinkedInText(nestedText)
                    }
                    else -> null
                }
                LinkedInCommentary(text = text)
            }
            else -> LinkedInCommentary(null)
        }
    }

    override fun serialize(encoder: Encoder, value: LinkedInCommentary) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        val obj = buildJsonObject {
            value.text?.text?.let { t ->
                put("text", buildJsonObject { put("text", t) })
            }
        }
        jsonEncoder.encodeJsonElement(obj)
    }
}

public object LinkedInFeedParser {

    public fun parse(jsonText: String): LinkedInFeedResponse {
        val root = try {
            JSON.parseToJsonElement(jsonText) as? JsonObject
        } catch (_: Exception) {
            return LinkedInFeedResponse()
        } ?: return LinkedInFeedResponse()

        val updates = mutableListOf<LinkedInUpdate>()

        // 1. Elements array (Direct or with "value" envelopes)
        val elementsArray = root["elements"] as? JsonArray
        elementsArray?.forEach { elem ->
            val update = extractUpdate(elem)
            if (update != null) {
                updates.add(update)
            }
        }

        // 2. Included array (Normalized Voyager format)
        if (updates.isEmpty()) {
            val includedArray = root["included"] as? JsonArray
            includedArray?.forEach { elem ->
                val type = (elem as? JsonObject)?.get("\$type")?.jsonPrimitive?.contentOrNull.orEmpty()
                if (type.contains("Update") || type.contains("Activity") || type.contains("Share") || type.contains("Feed")) {
                    val update = extractUpdate(elem)
                    if (update != null) {
                        updates.add(update)
                    }
                }
            }
        }

        // 3. Data.*elements or Data.elements or feedDashUpdates
        if (updates.isEmpty()) {
            val dataObj = root["data"] as? JsonObject
            val feedDash = (dataObj?.get("feedDashUpdates") as? JsonObject)
                ?: (root["feedDashUpdates"] as? JsonObject)
            val feedElements = (feedDash?.get("elements") as? JsonArray)
                ?: (feedDash?.get("*elements") as? JsonArray)
            feedElements?.forEach { elem ->
                extractUpdate(elem)?.let { updates.add(it) }
            }

            if (updates.isEmpty()) {
                val dataElements = (dataObj?.get("*elements") as? JsonArray)
                    ?: (dataObj?.get("elements") as? JsonArray)
                dataElements?.forEach { elem ->
                    extractUpdate(elem)?.let { updates.add(it) }
                }
            }
        }

        // Extract pagination token
        val metadataObj = root["metadata"] as? JsonObject
        val pagingObj = root["paging"] as? JsonObject
        val paginationToken = metadataObj?.get("paginationToken")?.jsonPrimitive?.contentOrNull
            ?: pagingObj?.get("paginationToken")?.jsonPrimitive?.contentOrNull
            ?: pagingObj?.get("next")?.jsonPrimitive?.contentOrNull

        return LinkedInFeedResponse(
            elements = updates.distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() },
            metadata = LinkedInFeedMetadata(
                paginationToken = paginationToken,
                count = updates.size,
            ),
        )
    }

    private fun extractUpdate(element: JsonElement): LinkedInUpdate? {
        val obj = element as? JsonObject ?: return null

        // Check if wrapped in value envelope
        val valueObj = obj["value"] as? JsonObject
        val targetObj = if (valueObj != null) {
            valueObj.values.filterIsInstance<JsonObject>().firstOrNull {
                it.containsKey("actor") || it.containsKey("urn") || it.containsKey("entityUrn")
            } ?: valueObj
        } else {
            obj
        }

        val decoded = try {
            JSON.decodeFromJsonElement(LinkedInUpdate.serializer(), targetObj)
        } catch (_: Exception) {
            extractUpdateFallback(targetObj)
        } ?: return null

        val resolvedId = decoded.id.ifBlank {
            decoded.urn ?: decoded.entityUrn ?: decoded.trackingUrn ?: ""
        }
        return decoded.copy(id = resolvedId)
    }

    private fun extractUpdateFallback(obj: JsonObject): LinkedInUpdate? {
        val urn = obj["urn"]?.jsonPrimitive?.contentOrNull
            ?: obj["entityUrn"]?.jsonPrimitive?.contentOrNull
            ?: obj["trackingUrn"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
            ?: return null

        val actorObj = obj["actor"] as? JsonObject
        val actorName = (actorObj?.get("name") as? JsonPrimitive)?.contentOrNull
            ?: (actorObj?.get("name") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            ?: (actorObj?.get("title") as? JsonPrimitive)?.contentOrNull
            ?: (actorObj?.get("title") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            ?: "LinkedIn Member"

        val actorHeadline = (actorObj?.get("description") as? JsonPrimitive)?.contentOrNull
            ?: (actorObj?.get("description") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            ?: (actorObj?.get("subtitle") as? JsonPrimitive)?.contentOrNull
            ?: (actorObj?.get("subtitle") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            ?: (actorObj?.get("subDescription") as? JsonPrimitive)?.contentOrNull
            ?: (actorObj?.get("subDescription") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull

        val commentaryText = (obj["commentary"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["commentary"] as? JsonObject)?.get("text")?.let {
                (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
            } ?: (obj["text"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["text"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull

        val actorNav = actorObj?.get("navigationUrl")?.jsonPrimitive?.contentOrNull

        // Extract avatar URL if present
        val avatarUrl = (actorObj?.get("image") as? JsonObject)?.let { imgObj ->
            imgObj["url"]?.jsonPrimitive?.contentOrNull
                ?: imgObj["sourceWebImageUrl"]?.jsonPrimitive?.contentOrNull
        }

        val actor = LinkedInActor(
            urn = actorObj?.get("urn")?.jsonPrimitive?.contentOrNull,
            entityUrn = actorObj?.get("entityUrn")?.jsonPrimitive?.contentOrNull,
            name = LinkedInText(actorName),
            title = LinkedInText(actorName),
            description = actorHeadline?.let { LinkedInText(it) },
            subtitle = actorHeadline?.let { LinkedInText(it) },
            navigationUrl = actorNav,
            image = avatarUrl?.let {
                LinkedInImageAttributes(
                    attributes = listOf(LinkedInImageDetail(sourceWebImageUrl = it)),
                    url = it,
                )
            },
        )

        return LinkedInUpdate(
            id = urn,
            urn = urn,
            entityUrn = urn,
            actor = actor,
            commentary = commentaryText?.let { LinkedInCommentary(LinkedInText(it)) },
        )
    }
}
