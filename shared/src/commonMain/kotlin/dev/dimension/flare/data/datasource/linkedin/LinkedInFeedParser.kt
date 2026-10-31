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
import kotlinx.serialization.json.Json
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
import kotlin.time.Clock

public object LinkedInTextSerializer : KSerializer<LinkedInText> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LinkedInText", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LinkedInText {
        val jsonDecoder = decoder as? JsonDecoder ?: return LinkedInText(decoder.decodeString())
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                LinkedInText(element.contentOrNull)
            }

            is JsonObject -> {
                val text =
                    element["text"]?.let {
                        (it as? JsonPrimitive)?.contentOrNull
                            ?: (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                    } ?: element["value"]?.jsonPrimitive?.contentOrNull
                LinkedInText(text)
            }

            else -> {
                LinkedInText(null)
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: LinkedInText,
    ) {
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
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: return LinkedInCommentary(LinkedInText(decoder.decodeString()))
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                LinkedInCommentary(LinkedInText(element.contentOrNull))
            }

            is JsonObject -> {
                val textElement = element["text"]
                val text =
                    when (textElement) {
                        is JsonPrimitive -> {
                            LinkedInText(textElement.contentOrNull)
                        }

                        is JsonObject -> {
                            val nestedText =
                                textElement["text"]?.let {
                                    (it as? JsonPrimitive)?.contentOrNull
                                } ?: textElement["value"]?.jsonPrimitive?.contentOrNull
                            LinkedInText(nestedText)
                        }

                        else -> {
                            null
                        }
                    }
                LinkedInCommentary(text = text)
            }

            else -> {
                LinkedInCommentary(null)
            }
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: LinkedInCommentary,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        val obj =
            buildJsonObject {
                value.text?.text?.let { t ->
                    put("text", buildJsonObject { put("text", t) })
                }
            }
        jsonEncoder.encodeJsonElement(obj)
    }
}

public object LinkedInFeedParser {
    public fun isValidLinkedInHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val lower = host.lowercase()
        return lower == "linkedin.com" || lower.endsWith(".linkedin.com") ||
            lower == "licdn.com" || lower.endsWith(".licdn.com")
    }

    public fun isValidFeedPayload(
        json: String,
        maxSizeBytes: Int = 2 * 1024 * 1024,
    ): Boolean {
        if (json.isBlank()) return false
        if (json.length > maxSizeBytes) return false
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return runCatching {
            val parsed = parse(json)
            parsed.elements.isNotEmpty()
        }.getOrDefault(false)
    }

    public const val MAX_DOM_PAYLOAD_BYTES: Int = 2 * 1024 * 1024

    public fun isValidDomPayload(
        json: String,
        maxSizeBytes: Int = MAX_DOM_PAYLOAD_BYTES,
    ): Boolean {
        if (json.isBlank() || json.length > maxSizeBytes) return false
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return false
        if (trimmed == "[]") return false
        return runCatching {
            val element = JSON.parseToJsonElement(trimmed)
            element is JsonArray && element.isNotEmpty() && element[0] is JsonObject
        }.getOrDefault(false)
    }

    public fun parseDomUpdates(json: String): List<LinkedInUpdate> {
        if (!isValidDomPayload(json)) return emptyList()
        return runCatching {
            val domUpdates = JSON.decodeFromString<List<LinkedInDomUpdate>>(json)
            domUpdates.mapNotNull { dom ->
                if (dom.urn.isBlank() && dom.text.isBlank() && dom.imageUrl.isBlank()) return@mapNotNull null
                val stableUrn = dom.urn.ifBlank { "urn:li:dom:${dom.authorName.hashCode()}_${dom.text.hashCode()}" }
                LinkedInUpdate(
                    id = stableUrn,
                    urn = stableUrn,
                    entityUrn = stableUrn,
                    actor =
                        LinkedInActor(
                            urn = "urn:li:member:${dom.authorName.hashCode()}",
                            name = LinkedInText(text = dom.authorName),
                            description = if (dom.authorHeadline.isNotBlank()) LinkedInText(text = dom.authorHeadline) else null,
                            image =
                                if (dom.avatarUrl.isNotBlank()) {
                                    LinkedInImageAttributes(
                                        url = dom.avatarUrl,
                                        sourceWebImageUrl = dom.avatarUrl,
                                    )
                                } else {
                                    null
                                },
                        ),
                    commentary = if (dom.text.isNotBlank()) LinkedInCommentary(text = LinkedInText(text = dom.text)) else null,
                    created = LinkedInTimestamp(time = Clock.System.now().toEpochMilliseconds()),
                    content =
                        if (dom.imageUrl.isNotBlank()) {
                            LinkedInContent(
                                imageComponent =
                                    LinkedInImageComponent(
                                        url = dom.imageUrl,
                                        altText = dom.text,
                                    ),
                            )
                        } else {
                            null
                        },
                )
            }
        }.getOrDefault(emptyList())
    }

    public fun parse(jsonText: String): LinkedInFeedResponse {
        val root =
            try {
                JSON.parseToJsonElement(jsonText) as? JsonObject
            } catch (_: Exception) {
                return LinkedInFeedResponse()
            } ?: return LinkedInFeedResponse()

        // Build entity dictionary from "included" normalized entities
        val includedMap = mutableMapOf<String, JsonObject>()
        val includedArray = root["included"] as? JsonArray
        includedArray?.filterIsInstance<JsonObject>()?.forEach { obj ->
            obj["entityUrn"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] = obj }
            obj["urn"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] = obj }
        }

        val updates = mutableListOf<LinkedInUpdate>()

        // 1. Elements array (Direct or with "value" envelopes)
        val elementsArray = root["elements"] as? JsonArray
        elementsArray?.forEach { elem ->
            val update = extractUpdate(elem, includedMap)
            if (update != null) {
                updates.add(update)
            }
        }

        // 2. Included array (Normalized Voyager format)
        if (includedArray != null) {
            includedArray.forEach { elem ->
                val type =
                    (elem as? JsonObject)
                        ?.get("\$type")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                if (type.contains("Update") || type.contains("Activity") || type.contains("Share") || type.contains("Feed")) {
                    val update = extractUpdate(elem, includedMap)
                    if (update != null) {
                        updates.add(update)
                    }
                }
            }
        }

        // 3. Data.*elements or Data.elements or feedDashUpdates (GraphQL)
        val dataObj = root["data"] as? JsonObject
        val feedDash =
            (dataObj?.get("feedDashUpdates") as? JsonObject)
                ?: (root["feedDashUpdates"] as? JsonObject)
        val feedElements =
            (feedDash?.get("elements") as? JsonArray)
                ?: (feedDash?.get("*elements") as? JsonArray)
        feedElements?.forEach { elem ->
            extractUpdate(elem, includedMap)?.let { updates.add(it) }
        }

        val dataElements =
            (dataObj?.get("*elements") as? JsonArray)
                ?: (dataObj?.get("elements") as? JsonArray)
        dataElements?.forEach { elem ->
            extractUpdate(elem, includedMap)?.let { updates.add(it) }
        }

        // Extract pagination token
        val metadataObj = root["metadata"] as? JsonObject
        val pagingObj = root["paging"] as? JsonObject
        val paginationToken =
            metadataObj?.get("paginationToken")?.jsonPrimitive?.contentOrNull
                ?: pagingObj?.get("paginationToken")?.jsonPrimitive?.contentOrNull
                ?: pagingObj?.get("next")?.jsonPrimitive?.contentOrNull

        val validUpdates =
            updates
                .filter { it.actor != null || it.commentary != null }
                .distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn ?: it.trackingUrn }.orEmpty() }

        return LinkedInFeedResponse(
            elements = validUpdates,
            metadata =
                LinkedInFeedMetadata(
                    paginationToken = paginationToken,
                    count = validUpdates.size,
                ),
        )
    }

    public fun parseComments(jsonText: String): LinkedInCommentsResponse {
        val root =
            try {
                JSON.parseToJsonElement(jsonText) as? JsonObject
            } catch (_: Exception) {
                return LinkedInCommentsResponse()
            } ?: return LinkedInCommentsResponse()

        val includedMap = mutableMapOf<String, JsonObject>()
        val includedArray = root["included"] as? JsonArray
        includedArray?.filterIsInstance<JsonObject>()?.forEach { obj ->
            obj["entityUrn"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] = obj }
            obj["urn"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] = obj }
        }

        val comments = mutableListOf<LinkedInComment>()

        // 1. Check elements (e.g. from /feed/dash/comments or GraphQL)
        val dataObj = root["data"] as? JsonObject
        val socialDetailObj = (dataObj?.get("socialDashCommentsBySocialDetail") as? JsonObject)
        val elementsArray =
            (root["elements"] as? JsonArray)
                ?: (dataObj?.get("elements") as? JsonArray)
                ?: (socialDetailObj?.get("*elements") as? JsonArray)
                ?: (socialDetailObj?.get("elements") as? JsonArray)
                ?: (dataObj?.get("socialDashNormCommentsByThreadUrn") as? JsonObject)?.get("elements") as? JsonArray
                ?: (dataObj?.get("voyagerSocialDashNormCommentsByThreadUrn") as? JsonObject)?.get("elements") as? JsonArray
                ?: (dataObj?.get("feedDashCommentsByUpdate") as? JsonObject)?.get("elements") as? JsonArray
                ?: (
                    dataObj
                        ?.values
                        ?.filterIsInstance<JsonObject>()
                        ?.mapNotNull {
                            (it["*elements"] as? JsonArray)
                                ?: (it["elements"] as? JsonArray)
                        }?.firstOrNull()
                )

        elementsArray?.forEach { elem ->
            extractComment(elem, includedMap)?.let { comments.add(it) }
        }

        // 2. Check included array for Comment types if elements didn't yield
        if (comments.isEmpty() && includedArray != null) {
            includedArray.forEach { elem ->
                val type =
                    (elem as? JsonObject)
                        ?.get("\$type")
                        ?.jsonPrimitive
                        ?.contentOrNull
                        .orEmpty()
                if (type.contains("Comment")) {
                    extractComment(elem, includedMap)?.let { comments.add(it) }
                }
            }
        }

        val pagingObj =
            (root["paging"] as? JsonObject)
                ?: (dataObj?.get("paging") as? JsonObject)
                ?: (
                    dataObj
                        ?.values
                        ?.filterIsInstance<JsonObject>()
                        ?.mapNotNull { it["paging"] as? JsonObject }
                        ?.firstOrNull()
                )
        val totalCount =
            pagingObj
                ?.get("total")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toLongOrNull()
        val paginationToken =
            (root["metadata"] as? JsonObject)?.get("paginationToken")?.jsonPrimitive?.contentOrNull
                ?: pagingObj?.get("paginationToken")?.jsonPrimitive?.contentOrNull

        return LinkedInCommentsResponse(
            comments = comments.distinctBy { it.id.ifBlank { it.urn ?: it.entityUrn }.orEmpty() },
            paginationToken = paginationToken,
            totalCount = totalCount ?: comments.size.toLong(),
        )
    }

    private fun extractComment(
        elem: JsonElement,
        includedMap: Map<String, JsonObject>,
    ): LinkedInComment? {
        val obj =
            (elem as? JsonObject)
                ?: (elem as? JsonPrimitive)?.contentOrNull?.let { includedMap[it] }
                ?: return null
        val targetObj = (obj["value"] as? JsonObject) ?: obj

        val urn = targetObj["urn"]?.jsonPrimitive?.contentOrNull
        val entityUrn = targetObj["entityUrn"]?.jsonPrimitive?.contentOrNull
        val id = urn ?: entityUrn ?: ""

        val commenter = resolveCommenter(targetObj, includedMap)
        val commentary =
            resolveCommentary(targetObj, includedMap)
                ?: targetObj["text"]?.let {
                    LinkedInCommentary(
                        LinkedInText(
                            (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull,
                        ),
                    )
                }

        if (commenter == null && commentary == null) return null

        val createdTime =
            (targetObj["created"] as? JsonObject)
                ?.get("time")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toLongOrNull()
                ?: targetObj["createdAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val created = createdTime?.let { LinkedInTimestamp(it) }

        val socialDetail = resolveSocialDetail(targetObj, includedMap)

        // Parse nested child comments / replies if present
        val childComments = mutableListOf<LinkedInComment>()
        val nestedArray =
            (targetObj["comments"] as? JsonArray)
                ?: ((targetObj["comments"] as? JsonObject)?.get("elements") as? JsonArray)
        nestedArray?.forEach { childElem ->
            extractComment(childElem, includedMap)?.let { childComments.add(it) }
        }

        return LinkedInComment(
            id = id,
            urn = urn,
            entityUrn = entityUrn,
            threadUrn = targetObj["threadUrn"]?.jsonPrimitive?.contentOrNull,
            commenter = commenter,
            commentary = commentary,
            created = created,
            socialDetail = socialDetail,
            comments = childComments,
        )
    }

    private fun resolveCommenter(
        obj: JsonObject,
        includedMap: Map<String, JsonObject>,
    ): LinkedInActor? {
        val commenterUrn =
            obj["*commenter"]?.jsonPrimitive?.contentOrNull
                ?: obj["*actor"]?.jsonPrimitive?.contentOrNull
                ?: obj["*commenterProfile"]?.jsonPrimitive?.contentOrNull
                ?: obj["commenter"]?.let { (it as? JsonObject)?.get("urn")?.jsonPrimitive?.contentOrNull }
        val commenterObj =
            (if (commenterUrn != null) includedMap[commenterUrn] else null)
                ?: (obj["commenter"] as? JsonObject)
                ?: (obj["actor"] as? JsonObject)
                ?: (obj["commenterProfile"] as? JsonObject)

        if (commenterObj != null) {
            val nestedActor =
                (commenterObj["actor"] as? JsonObject)
                    ?: (commenterObj["profile"] as? JsonObject)
                    ?: (commenterObj["miniProfile"] as? JsonObject)
                    ?: (commenterObj["commenter"] as? JsonObject)
            if (nestedActor != null) {
                return resolveActor(nestedActor, includedMap) ?: resolveActor(commenterObj, includedMap)
            }
            return resolveActor(commenterObj, includedMap)
        }
        return resolveActor(obj, includedMap)
    }

    private fun extractUpdate(
        element: JsonElement,
        includedMap: Map<String, JsonObject> = emptyMap(),
    ): LinkedInUpdate? {
        val obj = element as? JsonObject ?: return null

        // Check if wrapped in value envelope
        val valueObj = obj["value"] as? JsonObject
        val targetObj =
            if (valueObj != null) {
                valueObj.values.filterIsInstance<JsonObject>().firstOrNull {
                    it.containsKey("actor") || it.containsKey("*actor") || it.containsKey("urn") || it.containsKey("entityUrn")
                } ?: valueObj
            } else {
                obj
            }

        val decoded =
            try {
                JSON.decodeFromJsonElement(LinkedInUpdate.serializer(), targetObj)
            } catch (_: Exception) {
                extractUpdateFallback(targetObj, includedMap)
            } ?: return null

        val resolvedActor = resolveActor(targetObj, includedMap) ?: decoded.actor
        val resolvedCommentary = decoded.commentary ?: resolveCommentary(targetObj, includedMap)
        val resolvedContent = resolveContent(targetObj, includedMap) ?: decoded.content
        val resolvedSocialDetail = resolveSocialDetail(targetObj, includedMap) ?: decoded.socialDetail

        val resolvedId =
            decoded.id.ifBlank {
                decoded.urn ?: decoded.entityUrn ?: decoded.trackingUrn ?: ""
            }
        return decoded.copy(
            id = resolvedId,
            actor = resolvedActor,
            commentary = resolvedCommentary,
            content = resolvedContent,
            socialDetail = resolvedSocialDetail,
        )
    }

    private fun resolveActor(
        obj: JsonObject,
        includedMap: Map<String, JsonObject>,
    ): LinkedInActor? {
        val isDirectActor =
            obj.containsKey("name") || obj.containsKey("title") ||
                obj.containsKey("headline") || obj.containsKey("description") ||
                obj.containsKey("firstName") || obj.containsKey("lastName") ||
                obj.containsKey("publicIdentifier")

        val actorUrn =
            obj["*actor"]?.jsonPrimitive?.contentOrNull
                ?: obj["*commenter"]?.jsonPrimitive?.contentOrNull
                ?: (obj["actor"] as? JsonObject)?.get("urn")?.jsonPrimitive?.contentOrNull
                ?: (obj["commenter"] as? JsonObject)?.get("urn")?.jsonPrimitive?.contentOrNull

        val actorObj =
            (obj["actor"] as? JsonObject)
                ?: (obj["commenter"] as? JsonObject)
                ?: (if (actorUrn != null) includedMap[actorUrn] else null)
                ?: obj.takeIf { isDirectActor }
                ?: return null

        val firstName =
            (actorObj["firstName"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["firstName"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        val lastName =
            (actorObj["lastName"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["lastName"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
        val fullName =
            if (!firstName.isNullOrBlank() || !lastName.isNullOrBlank()) {
                listOfNotNull(firstName, lastName).joinToString(" ").trim()
            } else {
                null
            }

        val actorName =
            (actorObj["name"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["name"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?: fullName
                ?: (actorObj["title"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["title"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?: "LinkedIn Member"

        val actorHeadline =
            (actorObj["description"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["description"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?: (actorObj["subtitle"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["subtitle"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?: (actorObj["headline"] as? JsonPrimitive)?.contentOrNull
                ?: (actorObj["headline"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull

        val publicIdentifier = actorObj["publicIdentifier"]?.jsonPrimitive?.contentOrNull
        val actorNav =
            actorObj["navigationUrl"]?.jsonPrimitive?.contentOrNull
                ?: if (!publicIdentifier.isNullOrBlank()) "https://www.linkedin.com/in/$publicIdentifier" else null

        val imageObj =
            actorObj["image"] as? JsonObject
                ?: actorObj["picture"] as? JsonObject
                ?: actorObj["*image"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] }
                ?: actorObj["*picture"]?.jsonPrimitive?.contentOrNull?.let { includedMap[it] }

        val avatarUrl = extractAvatarFromImageObj(imageObj)

        val resolvedUrn =
            actorObj["urn"]?.jsonPrimitive?.contentOrNull
                ?: actorObj["entityUrn"]?.jsonPrimitive?.contentOrNull
                ?: actorUrn

        return LinkedInActor(
            urn = resolvedUrn,
            entityUrn = resolvedUrn,
            name = LinkedInText(actorName),
            title = LinkedInText(actorName),
            description = actorHeadline?.let { LinkedInText(it) },
            subtitle = actorHeadline?.let { LinkedInText(it) },
            navigationUrl = actorNav,
            image =
                avatarUrl?.let {
                    LinkedInImageAttributes(
                        attributes = listOf(LinkedInImageDetail(sourceWebImageUrl = it)),
                        url = it,
                    )
                },
        )
    }

    private fun resolveCommentary(
        obj: JsonObject,
        includedMap: Map<String, JsonObject>,
    ): LinkedInCommentary? {
        val commentaryUrn = obj["*commentary"]?.jsonPrimitive?.contentOrNull
        val targetObj =
            (if (commentaryUrn != null) includedMap[commentaryUrn] else null)
                ?: (obj["commentary"] as? JsonObject)
                ?: return null

        val text =
            (targetObj["text"] as? JsonPrimitive)?.contentOrNull
                ?: (targetObj["text"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                ?: targetObj["value"]?.jsonPrimitive?.contentOrNull
                ?: return null

        return LinkedInCommentary(LinkedInText(text))
    }

    private fun resolveSocialDetail(
        obj: JsonObject,
        includedMap: Map<String, JsonObject>,
    ): LinkedInSocialDetail? {
        val socialDetailUrn = obj["*socialDetail"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val socialObj =
            (if (socialDetailUrn != null) includedMap[socialDetailUrn] else null)
                ?: (obj["socialDetail"] as? JsonObject)

        val countsUrn =
            socialObj?.get("*totalSocialActivityCounts")?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: obj["*totalSocialActivityCounts"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: obj["*socialActivityCounts"]?.let { (it as? JsonPrimitive)?.contentOrNull }

        val countsObj =
            (if (countsUrn != null) includedMap[countsUrn] else null)
                ?: (socialObj?.get("totalSocialActivityCounts") as? JsonObject)
                ?: (obj["totalSocialActivityCounts"] as? JsonObject)
                ?: (obj["socialActivityCounts"] as? JsonObject)

        val numLikes = countsObj?.get("numLikes")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
        val numComments = countsObj?.get("numComments")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
        val numShares = countsObj?.get("numShares")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }

        val reactionType = socialObj?.get("reactionType")?.let { (it as? JsonPrimitive)?.contentOrNull }
        val hasLiked =
            (socialObj?.get("hasLiked") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
                ?: (socialObj?.get("liked") as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
                ?: (reactionType != null && !reactionType.equals("NONE", ignoreCase = true))

        if (numLikes == null && numComments == null && numShares == null && hasLiked != true) {
            return null
        }

        return LinkedInSocialDetail(
            totalSocialActivityCounts =
                LinkedInSocialCounts(
                    numLikes = numLikes ?: 0L,
                    numComments = numComments ?: 0L,
                    numShares = numShares ?: 0L,
                ),
            reactions =
                LinkedInReactions(
                    hasLiked = hasLiked,
                    reactionType = reactionType,
                ),
        )
    }

    internal fun extractVectorImageUrl(container: JsonElement?): String? {
        val obj = container as? JsonObject ?: return null
        val directUrl =
            obj["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: obj["sourceWebImageUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (!directUrl.isNullOrBlank()) return directUrl

        val vectorObj =
            (obj["com.linkedin.common.VectorImage"] as? JsonObject)
                ?: (obj["vectorImage"] as? JsonObject)
                ?: obj
        val rootUrl = vectorObj["rootUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
        val artifacts = vectorObj["artifacts"] as? JsonArray ?: return null
        val validArtifact =
            artifacts.filterIsInstance<JsonObject>().lastOrNull {
                it["fileIdentifyingUrlPathSegment"]?.let { seg -> (seg as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true } ?: false
            } ?: artifacts.filterIsInstance<JsonObject>().firstOrNull {
                it["fileIdentifyingUrlPathSegment"]?.let { seg -> (seg as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true } ?: false
            }
        val path = validArtifact?.get("fileIdentifyingUrlPathSegment")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return null
        val separator = if (rootUrl.endsWith("/") || rootUrl.endsWith("_") || path.startsWith("/")) "" else "/"
        return "$rootUrl$separator$path"
    }

    internal fun extractAvatarFromImageObj(imageObj: JsonObject?): String? {
        if (imageObj == null) return null
        val direct =
            imageObj["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                ?: imageObj["sourceWebImageUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (!direct.isNullOrBlank()) return direct

        val attrs = imageObj["attributes"] as? JsonArray
        if (attrs != null) {
            for (attr in attrs.filterIsInstance<JsonObject>()) {
                val sourceWeb = attr["sourceWebImageUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (!sourceWeb.isNullOrBlank()) return sourceWeb

                val miniCompany = attr["miniCompany"] as? JsonObject
                val companyLogo = extractVectorImageUrl(miniCompany?.get("logo"))
                if (!companyLogo.isNullOrBlank()) return companyLogo

                val miniProfile = attr["miniProfile"] as? JsonObject
                val profilePic = extractVectorImageUrl(miniProfile?.get("picture"))
                if (!profilePic.isNullOrBlank()) return profilePic

                val detailData = attr["detailData"] as? JsonObject
                val vectorUrl =
                    extractVectorImageUrl(detailData?.get("vectorImage"))
                        ?: extractVectorImageUrl((detailData?.get("nonEntityProfilePicture") as? JsonObject)?.get("vectorImage"))
                if (!vectorUrl.isNullOrBlank()) return vectorUrl
                val nonEntity =
                    (
                        detailData?.get(
                            "nonEntityProfilePicture",
                        ) as? JsonObject
                    )?.get("url")?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (!nonEntity.isNullOrBlank()) return nonEntity
            }
        }
        return extractVectorImageUrl(imageObj)
    }

    private fun resolveContent(
        obj: JsonObject,
        includedMap: Map<String, JsonObject>,
    ): LinkedInContent? {
        val contentUrn = obj["*content"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val targetObj =
            (if (contentUrn != null) includedMap[contentUrn] else null)
                ?: (obj["content"] as? JsonObject)
                ?: return null

        val decoded =
            try {
                JSON.decodeFromJsonElement(LinkedInContent.serializer(), targetObj)
            } catch (_: Exception) {
                null
            }

        val imageCompObj =
            (targetObj["com.linkedin.voyager.feed.render.ImageComponent"] as? JsonObject)
                ?: (targetObj["imageComponent"] as? JsonObject)
        val resolvedImage =
            if (imageCompObj != null) {
                val imagesArray = imageCompObj["images"] as? JsonArray
                val firstImageObj = imagesArray?.firstOrNull() as? JsonObject
                val altText =
                    firstImageObj?.get("accessibilityText")?.let { (it as? JsonPrimitive)?.contentOrNull }
                        ?: firstImageObj?.get("accessibilityTextSourceType")?.let { (it as? JsonPrimitive)?.contentOrNull }
                val imageUrl =
                    firstImageObj?.let { img ->
                        val direct = img["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                        if (!direct.isNullOrBlank()) {
                            direct
                        } else {
                            val attrs = img["attributes"] as? JsonArray
                            val firstAttr = attrs?.firstOrNull() as? JsonObject
                            extractVectorImageUrl(firstAttr?.get("vectorImage") ?: firstAttr)
                        }
                    } ?: extractVectorImageUrl(imageCompObj)
                imageUrl?.let {
                    LinkedInImageComponent(
                        url = it,
                        altText = altText,
                    )
                }
            } else {
                decoded?.imageComponent
            }

        val multiImages =
            if (imageCompObj != null) {
                val imagesArray = imageCompObj["images"] as? JsonArray
                val allImgs =
                    imagesArray
                        ?.filterIsInstance<JsonObject>()
                        ?.mapNotNull { img ->
                            val alt = img["accessibilityText"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                            val direct = img["url"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                            val url =
                                if (!direct.isNullOrBlank()) {
                                    direct
                                } else {
                                    val attrs = img["attributes"] as? JsonArray
                                    val firstAttr = attrs?.firstOrNull() as? JsonObject
                                    extractVectorImageUrl(firstAttr?.get("vectorImage") ?: firstAttr)
                                }
                            url?.let { LinkedInImageComponent(url = it, altText = alt) }
                        }.orEmpty()
                if (allImgs.size > 1) LinkedInMultiImagesComponent(allImgs) else decoded?.imagesComponent
            } else {
                decoded?.imagesComponent
            }

        val articleCompObj =
            (targetObj["com.linkedin.voyager.feed.render.ArticleComponent"] as? JsonObject)
                ?: (targetObj["articleComponent"] as? JsonObject)
        val resolvedArticle =
            if (articleCompObj != null) {
                val title =
                    (articleCompObj["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (articleCompObj["title"] as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
                val subtitle =
                    (articleCompObj["subtitle"] as? JsonPrimitive)?.contentOrNull
                        ?: (articleCompObj["subtitle"] as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
                val navUrl = articleCompObj["navigationUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                val thumb =
                    extractVectorImageUrl(articleCompObj["thumbnail"])
                        ?: articleCompObj["thumbnail"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                LinkedInArticleComponent(
                    title = title,
                    subtitle = subtitle,
                    navigationUrl = navUrl,
                    thumbnail = thumb,
                )
            } else {
                decoded?.articleComponent
            }

        val videoCompObj =
            (targetObj["com.linkedin.voyager.feed.render.LinkedInVideoComponent"] as? JsonObject)
                ?: (targetObj["videoComponent"] as? JsonObject)
        val resolvedVideo =
            if (videoCompObj != null) {
                val meta = videoCompObj["videoPlayMetadata"] as? JsonObject
                val thumb = extractVectorImageUrl(meta?.get("thumbnail") ?: videoCompObj["thumbnailUrl"])
                LinkedInVideoComponent(
                    url = videoCompObj["url"]?.let { (it as? JsonPrimitive)?.contentOrNull },
                    thumbnailUrl = thumb,
                )
            } else {
                decoded?.videoComponent
            }

        val docCompObj =
            (targetObj["com.linkedin.voyager.feed.render.DocumentComponent"] as? JsonObject)
                ?: (targetObj["documentComponent"] as? JsonObject)
        val resolvedDoc =
            if (docCompObj != null) {
                val title =
                    (docCompObj["title"] as? JsonPrimitive)?.contentOrNull
                        ?: (docCompObj["title"] as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull }
                val pageCount = docCompObj["totalPageCount"]?.let { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
                val nav = docCompObj["navigationUrl"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                val thumb =
                    extractVectorImageUrl(docCompObj["thumbnail"])
                        ?: docCompObj["thumbnail"]?.let { (it as? JsonPrimitive)?.contentOrNull }
                LinkedInDocumentComponent(
                    title = title,
                    totalPageCount = pageCount,
                    navigationUrl = nav,
                    thumbnail = thumb,
                )
            } else {
                decoded?.documentComponent
            }

        return (decoded ?: LinkedInContent()).copy(
            imageComponent = resolvedImage,
            imagesComponent = multiImages,
            articleComponent = resolvedArticle,
            videoComponent = resolvedVideo,
            documentComponent = resolvedDoc,
        )
    }

    private fun extractUpdateFallback(
        obj: JsonObject,
        includedMap: Map<String, JsonObject> = emptyMap(),
    ): LinkedInUpdate? {
        val urn =
            obj["urn"]?.jsonPrimitive?.contentOrNull
                ?: obj["entityUrn"]?.jsonPrimitive?.contentOrNull
                ?: obj["trackingUrn"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull
                ?: return null

        val actor =
            resolveActor(obj, includedMap) ?: run {
                val actorObj = obj["actor"] as? JsonObject
                val actorName =
                    (actorObj?.get("name") as? JsonPrimitive)?.contentOrNull
                        ?: (actorObj?.get("name") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: (actorObj?.get("title") as? JsonPrimitive)?.contentOrNull
                        ?: (actorObj?.get("title") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: "LinkedIn Member"

                val actorHeadline =
                    (actorObj?.get("description") as? JsonPrimitive)?.contentOrNull
                        ?: (actorObj?.get("description") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: (actorObj?.get("subtitle") as? JsonPrimitive)?.contentOrNull
                        ?: (actorObj?.get("subtitle") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: (actorObj?.get("subDescription") as? JsonPrimitive)?.contentOrNull
                        ?: (actorObj?.get("subDescription") as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull

                val actorNav = actorObj?.get("navigationUrl")?.jsonPrimitive?.contentOrNull

                val avatarUrl =
                    (actorObj?.get("image") as? JsonObject)?.let { imgObj ->
                        imgObj["url"]?.jsonPrimitive?.contentOrNull
                            ?: imgObj["sourceWebImageUrl"]?.jsonPrimitive?.contentOrNull
                    }

                LinkedInActor(
                    urn = actorObj?.get("urn")?.jsonPrimitive?.contentOrNull,
                    entityUrn = actorObj?.get("entityUrn")?.jsonPrimitive?.contentOrNull,
                    name = LinkedInText(actorName),
                    title = LinkedInText(actorName),
                    description = actorHeadline?.let { LinkedInText(it) },
                    subtitle = actorHeadline?.let { LinkedInText(it) },
                    navigationUrl = actorNav,
                    image =
                        avatarUrl?.let {
                            LinkedInImageAttributes(
                                attributes = listOf(LinkedInImageDetail(sourceWebImageUrl = it)),
                                url = it,
                            )
                        },
                )
            }

        val commentary =
            resolveCommentary(obj, includedMap) ?: run {
                val commentaryText =
                    (obj["commentary"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["commentary"] as? JsonObject)?.get("text")?.let {
                            (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                        } ?: (obj["text"] as? JsonPrimitive)?.contentOrNull
                        ?: (obj["text"] as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                commentaryText?.let { LinkedInCommentary(LinkedInText(it)) }
            }

        val content = resolveContent(obj, includedMap)
        val socialDetail = resolveSocialDetail(obj, includedMap)

        return LinkedInUpdate(
            id = urn,
            urn = urn,
            entityUrn = urn,
            actor = actor,
            commentary = commentary,
            content = content,
            socialDetail = socialDetail,
        )
    }
}
