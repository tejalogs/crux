package org.crux.data.database.cache.model

import kotlinx.collections.immutable.toPersistentList
import org.crux.common.Locale
import org.crux.common.encodeJson
import org.crux.data.datasource.microblog.ActionMenu
import org.crux.data.datasource.microblog.PostActionFamily
import org.crux.data.translation.PreTranslationStoreSupport
import org.crux.model.AccountType
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.DeeplinkEvent
import org.crux.ui.model.TranslationDisplayState
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiProfile
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.render.toUiPlainText
import org.crux.ui.route.DeeplinkRoute

internal data class TranslationDisplayOptions(
    val translationEnabled: Boolean,
    val autoDisplayEnabled: Boolean,
    val providerCacheKey: String,
    val preferPlatformTranslation: Boolean = false,
)

internal fun UiTimelineV2.applyTranslation(
    options: TranslationDisplayOptions,
    translations: List<DbTranslation>,
): UiTimelineV2 {
    if (!options.translationEnabled) {
        return this
    }
    if (this is UiTimelineV2.TimelinePostItem) {
        val translatedPost = post.applyTranslation(options, translations) as UiTimelineV2.Post
        val translatedParents =
            presentation.inlineParents.mapTranslationIfChanged {
                it.applyTranslation(options, translations) as UiTimelineV2.TimelinePostItem
            }
        val translatedQuotes =
            presentation.quotes.mapTranslationIfChanged {
                it.applyTranslation(
                    options,
                    translations,
                ) as UiTimelineV2.Post
            }
        val translatedRepost = presentation.repost?.applyTranslation(options, translations) as? UiTimelineV2.Post
        if (
            translatedPost === post &&
            translatedParents === presentation.inlineParents &&
            translatedQuotes === presentation.quotes &&
            translatedRepost === presentation.repost
        ) {
            return this
        }
        return copy(
            post = translatedPost,
            presentation =
                presentation.copy(
                    inlineParents = translatedParents,
                    quotes = translatedQuotes,
                    repost = translatedRepost,
                ),
        )
    }
    val payload = translationPayload() ?: return this
    val cacheKey =
        effectiveTranslationCacheKey(
            providerCacheKey = options.providerCacheKey,
            preferPlatformTranslation = options.preferPlatformTranslation,
        )
    val translation =
        translations.firstOrNull {
            it.targetLanguage == Locale.language &&
                (
                    it.sourceHash == payload.sourceHash(options.providerCacheKey) ||
                        (options.preferPlatformTranslation && it.matchesPlatformTranslation(payload, cacheKey))
                )
        }

    return when (this) {
        is UiTimelineV2.Feed -> {
            val translatedTitle =
                translation
                    .takeIf { it?.status == TranslationStatus.Completed }
                    ?.payload
                    ?.title
                    ?.raw ?: title
            val translatedDescription =
                translation
                    .takeIf { it?.status == TranslationStatus.Completed }
                    ?.payload
                    ?.description
                    ?.raw ?: description
            val displayState = translation.toDisplayState()
            if (
                translatedTitle == title &&
                translatedDescription == description &&
                displayState == translationDisplayState
            ) {
                this
            } else {
                copy(
                    title = translatedTitle,
                    description = translatedDescription,
                    translationDisplayState = displayState,
                )
            }
        }

        is UiTimelineV2.Post -> {
            run {
                val displayMode = translation?.displayMode ?: TranslationDisplayMode.Auto
                val translatedPayload = translation?.takeIf { it.status == TranslationStatus.Completed }?.payload
                val shouldShowTranslated =
                    translatedPayload != null &&
                        when (displayMode) {
                            TranslationDisplayMode.Translated -> true
                            TranslationDisplayMode.Original -> false
                            TranslationDisplayMode.Auto -> options.autoDisplayEnabled
                        }
                val displayState =
                    when {
                        translation?.status == TranslationStatus.Completed && shouldShowTranslated -> TranslationDisplayState.Translated
                        translation?.status == TranslationStatus.Completed -> TranslationDisplayState.Hidden
                        else -> translation.toDisplayState()
                    }
                val menuAction =
                    when {
                        translation?.status == TranslationStatus.Failed -> {
                            TranslationMenuAction.Retry
                        }

                        shouldShowTranslated -> {
                            TranslationMenuAction.ShowOriginal
                        }

                        translation?.status == TranslationStatus.Pending || translation?.status == TranslationStatus.Translating -> {
                            TranslationMenuAction.TranslateNoop
                        }

                        PreTranslationStoreSupport.canRetrySkippedManually(translation) -> {
                            TranslationMenuAction.Translate
                        }

                        translation?.status == TranslationStatus.Skipped -> {
                            null
                        }

                        else -> {
                            TranslationMenuAction.Translate
                        }
                    }
                val translatedContent =
                    translatedPayload
                        ?.let { payload ->
                            if (content.translation == payload.content) {
                                content
                            } else {
                                content.copy(translation = payload.content)
                            }
                        } ?: content
                val translatedWarning =
                    translatedPayload
                        ?.let { payload ->
                            if (contentWarning?.translation == payload.contentWarning) {
                                contentWarning
                            } else {
                                contentWarning?.copy(translation = payload.contentWarning)
                            }
                        } ?: contentWarning
                val translatedActions =
                    actions
                        .withTranslationMenuAction(menuAction, accountType, statusKey)
                        .withSmartReplyMenuAction(
                            accountType = accountType,
                            statusKey = statusKey,
                            authorName = user?.name?.raw.orEmpty(),
                            authorHandle = user?.handle?.raw.orEmpty(),
                            postText = content.original.raw.ifBlank { content.translation?.raw.orEmpty() },
                        )
                if (
                    translatedContent === content &&
                    translatedWarning === contentWarning &&
                    displayState == translationDisplayState &&
                    translatedActions === actions
                ) {
                    this
                } else {
                    copy(
                        content = translatedContent,
                        contentWarning = translatedWarning,
                        translationDisplayState = displayState,
                        actions = translatedActions,
                    )
                }
            }
        }

        is UiTimelineV2.Message -> {
            this
        }

        is UiTimelineV2.User -> {
            this
        }

        is UiTimelineV2.UserList -> {
            this
        }
    }
}

/** Applies each status's own translation while preserving unchanged subtrees by reference. */
internal fun UiTimelineV2.applyTranslation(
    options: TranslationDisplayOptions,
    translationsFor: (
        accountType: org.crux.model.AccountType,
        statusKey: org.crux.model.MicroBlogKey,
    ) -> List<DbTranslation>,
): UiTimelineV2 {
    if (!options.translationEnabled) {
        return this
    }
    return when (this) {
        is UiTimelineV2.TimelinePostItem -> {
            val translatedPost = post.applyTranslation(options, translationsFor(post.accountType, post.statusKey)) as UiTimelineV2.Post
            val translatedParents =
                presentation.inlineParents.mapTranslationIfChanged {
                    it.applyTranslation(options, translationsFor) as UiTimelineV2.TimelinePostItem
                }
            val translatedQuotes =
                presentation.quotes.mapTranslationIfChanged {
                    it.applyTranslation(options, translationsFor(it.accountType, it.statusKey)) as UiTimelineV2.Post
                }
            val translatedRepost =
                presentation.repost?.let {
                    it.applyTranslation(options, translationsFor(it.accountType, it.statusKey)) as UiTimelineV2.Post
                }
            if (
                translatedPost === post &&
                translatedParents === presentation.inlineParents &&
                translatedQuotes === presentation.quotes &&
                translatedRepost === presentation.repost
            ) {
                this
            } else {
                copy(
                    post = translatedPost,
                    presentation =
                        presentation.copy(
                            inlineParents = translatedParents,
                            quotes = translatedQuotes,
                            repost = translatedRepost,
                        ),
                )
            }
        }

        is UiTimelineV2.UserList -> {
            val translatedPost =
                post?.let {
                    it.applyTranslation(options, translationsFor(it.accountType, it.statusKey)) as UiTimelineV2.Post
                }
            if (translatedPost === post) this else copy(post = translatedPost)
        }

        else -> {
            applyTranslation(options, translationsFor(accountType, statusKey))
        }
    }
}

internal fun UiProfile.applyTranslation(
    options: TranslationDisplayOptions,
    translation: DbTranslation?,
): UiProfile {
    if (!options.autoDisplayEnabled) {
        return this
    }
    val payload = translationPayload()
    val matchedTranslation =
        translation?.takeIf {
            it.targetLanguage == Locale.language &&
                it.sourceHash == payload.sourceHash(options.providerCacheKey)
        }
    val displayState = matchedTranslation.toDisplayState()
    val translatedDescription =
        matchedTranslation.takeIf { it?.status == TranslationStatus.Completed }?.payload?.description ?: description
    return if (translatedDescription == description && displayState == translationDisplayState) {
        this
    } else {
        copy(
            description = translatedDescription,
            translationDisplayState = displayState,
        )
    }
}

private inline fun <T> kotlinx.collections.immutable.ImmutableList<T>.mapTranslationIfChanged(
    transform: (T) -> T,
): kotlinx.collections.immutable.ImmutableList<T> {
    var result: MutableList<T>? = null
    forEachIndexed { index, post ->
        val translated = transform(post)
        if (result != null) {
            result.add(translated)
        } else if (translated !== post) {
            result =
                ArrayList<T>(size).also { copy ->
                    repeat(index) { copy += this[it] }
                    copy += translated
                }
        }
    }
    return result?.toPersistentList() ?: this
}

internal fun UiTimelineV2.translationPayload(): TranslationPayload? =
    when (this) {
        is UiTimelineV2.Feed -> {
            TranslationPayload(
                title = title?.toUiPlainText(sourceLanguages),
                description = description?.toUiPlainText(sourceLanguages),
            )
        }

        is UiTimelineV2.Post -> {
            TranslationPayload(
                content = content.original,
                contentWarning = contentWarning?.original,
            )
        }

        is UiTimelineV2.TimelinePostItem -> {
            displayPost.translationPayload()
        }

        is UiTimelineV2.Message -> {
            null
        }

        is UiTimelineV2.User -> {
            null
        }

        is UiTimelineV2.UserList -> {
            null
        }
    }

internal fun UiProfile.translationPayload(): TranslationPayload =
    TranslationPayload(
        description = description,
    )

internal fun TranslationPayload.sourceHash(providerCacheKey: String): String =
    buildString {
        append(providerCacheKey)
        append('\u0000')
        append(encodeJson(TranslationPayload.serializer()))
    }.stableTranslationHash()

private fun UiTimelineV2.platformPostOrNull(): UiTimelineV2.Post? =
    when (this) {
        is UiTimelineV2.Post -> this
        is UiTimelineV2.TimelinePostItem -> displayPost
        else -> null
    }

private fun UiTimelineV2.Post.platformTranslationPayloadOrNull(): TranslationPayload? =
    TranslationPayload(
        content = content.translation,
        contentWarning = contentWarning?.translation,
    ).takeIf { it.content != null || it.contentWarning != null }

internal fun UiTimelineV2.platformTranslationPayload(): TranslationPayload? = platformPostOrNull()?.platformTranslationPayloadOrNull()

internal fun UiTimelineV2.effectiveTranslationCacheKey(
    providerCacheKey: String,
    preferPlatformTranslation: Boolean,
): String {
    if (!preferPlatformTranslation) {
        return providerCacheKey
    }
    val post = platformPostOrNull() ?: return providerCacheKey
    return "platform:${post.platformId}"
}

internal fun DbTranslation.matchesPlatformTranslation(
    source: TranslationPayload,
    platformCacheKey: String,
): Boolean {
    if (sourceHash == source.sourceHash(platformCacheKey)) {
        return true
    }
    // Legacy keys included the translated payload; validate them against the current original.
    val translated = payload ?: return false
    val payloadHash = translated.encodeJson(TranslationPayload.serializer()).stableTranslationHash()
    return sourceHash == source.sourceHash("$platformCacheKey:$payloadHash")
}

private fun DbTranslation?.toDisplayState(): TranslationDisplayState =
    when (this?.status) {
        TranslationStatus.Pending,
        TranslationStatus.Translating,
        -> TranslationDisplayState.Translating

        TranslationStatus.Completed -> TranslationDisplayState.Translated

        TranslationStatus.Failed -> TranslationDisplayState.Failed

        TranslationStatus.Skipped,
        null,
        -> TranslationDisplayState.Hidden
    }

private fun kotlinx.collections.immutable.ImmutableList<ActionMenu>.withTranslationMenuAction(
    action: TranslationMenuAction?,
    accountType: org.crux.model.AccountType,
    statusKey: org.crux.model.MicroBlogKey,
) = if (action == null) {
    this
} else if (accountType is AccountType.Specific) {
    var changed = false
    val mapped =
        map { menu ->
            menu
                .prependTranslationAction(
                    accountKey = accountType.accountKey,
                    statusKey = statusKey,
                    translationAction = action,
                ).also { changed = changed || it !== menu }
        }
    if (!changed) this else mapped.toPersistentList()
} else {
    this
}

private fun ActionMenu.prependTranslationAction(
    accountKey: org.crux.model.MicroBlogKey,
    statusKey: org.crux.model.MicroBlogKey,
    translationAction: TranslationMenuAction,
): ActionMenu =
    when (this) {
        is ActionMenu.Group -> {
            if (displayItem.text.isMoreMenuText()) {
                val localAction =
                    ActionMenu.Item(
                        text =
                            ActionMenu.Item.Text.Localized(
                                when (translationAction) {
                                    TranslationMenuAction.Retry -> ActionMenu.Item.Text.Localized.Type.RetryTranslation

                                    TranslationMenuAction.Translate,
                                    TranslationMenuAction.TranslateNoop,
                                    -> ActionMenu.Item.Text.Localized.Type.Translate

                                    TranslationMenuAction.ShowOriginal -> ActionMenu.Item.Text.Localized.Type.ShowOriginal
                                },
                            ),
                        clickEvent =
                            when (translationAction) {
                                TranslationMenuAction.TranslateNoop -> {
                                    ClickEvent.Noop
                                }

                                else -> {
                                    ClickEvent.Deeplink(
                                        DeeplinkEvent(
                                            accountKey = accountKey,
                                            translationEvent =
                                                when (translationAction) {
                                                    TranslationMenuAction.Retry -> {
                                                        DeeplinkEvent.TranslationEvent.RetryTranslation(
                                                            statusKey,
                                                        )
                                                    }

                                                    TranslationMenuAction.Translate -> {
                                                        DeeplinkEvent.TranslationEvent.Translate(statusKey)
                                                    }

                                                    TranslationMenuAction.ShowOriginal -> {
                                                        DeeplinkEvent.TranslationEvent.ShowOriginal(
                                                            statusKey,
                                                        )
                                                    }

                                                    TranslationMenuAction.TranslateNoop -> {
                                                        error("TranslateNoop should use ClickEvent.Noop")
                                                    }
                                                },
                                        ),
                                    )
                                }
                            },
                        icon = UiIcon.Translate,
                        actionFamily = PostActionFamily.Translate,
                    )
                if (
                    actions.firstOrNull() == localAction &&
                    actions.drop(1).none { it.isTranslationAction() }
                ) {
                    return this
                }
                copy(
                    actions =
                        (
                            listOf(localAction) +
                                actions.filterNot { it.isTranslationAction() }
                        ).toPersistentList(),
                )
            } else {
                this
            }
        }

        is ActionMenu.Item,
        ActionMenu.Divider,
        -> {
            this
        }
    }

private fun ActionMenu.isTranslationAction(): Boolean {
    val localized = (this as? ActionMenu.Item)?.text as? ActionMenu.Item.Text.Localized
    return localized?.type == ActionMenu.Item.Text.Localized.Type.RetryTranslation ||
        localized?.type == ActionMenu.Item.Text.Localized.Type.Translate ||
        localized?.type == ActionMenu.Item.Text.Localized.Type.ShowOriginal
}

private fun ActionMenu.isSmartReplyAction(): Boolean {
    val localized = (this as? ActionMenu.Item)?.text as? ActionMenu.Item.Text.Localized
    return localized?.type == ActionMenu.Item.Text.Localized.Type.SmartReply
}

private fun kotlinx.collections.immutable.ImmutableList<ActionMenu>.withSmartReplyMenuAction(
    accountType: org.crux.model.AccountType,
    statusKey: org.crux.model.MicroBlogKey,
    authorName: String,
    authorHandle: String,
    postText: String,
): kotlinx.collections.immutable.ImmutableList<ActionMenu> {
    if (accountType !is AccountType.Specific) return this
    if (postText.isBlank()) return this
    var changed = false
    val mapped =
        map { menu ->
            menu
                .prependSmartReplyAction(
                    accountKey = accountType.accountKey,
                    statusKey = statusKey,
                    authorName = authorName,
                    authorHandle = authorHandle,
                    postText = postText,
                ).also { changed = changed || it !== menu }
        }
    return if (!changed) this else mapped.toPersistentList()
}

private fun ActionMenu.prependSmartReplyAction(
    accountKey: org.crux.model.MicroBlogKey,
    statusKey: org.crux.model.MicroBlogKey,
    authorName: String,
    authorHandle: String,
    postText: String,
): ActionMenu =
    when (this) {
        is ActionMenu.Group -> {
            if (displayItem.text.isMoreMenuText()) {
                val smartReplyAction =
                    ActionMenu.Item(
                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.SmartReply),
                        clickEvent =
                            ClickEvent.Deeplink(
                                DeeplinkRoute.Status.SmartReply(
                                    statusKey = statusKey,
                                    accountType = AccountType.Specific(accountKey),
                                    initialText = postText,
                                    authorName = authorName,
                                    authorHandle = authorHandle,
                                ),
                            ),
                        icon = UiIcon.Reply,
                        actionFamily = PostActionFamily.Reply,
                    )
                if (actions.any { it.isSmartReplyAction() }) {
                    this
                } else {
                    val translationIndex = actions.indexOfFirst { it.isTranslationAction() }
                    val insertIndex = if (translationIndex >= 0) translationIndex + 1 else 0
                    val mutable = actions.toMutableList()
                    mutable.add(insertIndex.coerceAtMost(mutable.size), smartReplyAction)
                    copy(
                        actions = mutable.toPersistentList(),
                    )
                }
            } else {
                this
            }
        }

        else -> {
            this
        }
    }

private fun ActionMenu.Item.Text?.isMoreMenuText(): Boolean =
    (this as? ActionMenu.Item.Text.Localized)?.type == ActionMenu.Item.Text.Localized.Type.More

private enum class TranslationMenuAction {
    Retry,
    Translate,
    TranslateNoop,
    ShowOriginal,
}

private fun String.stableTranslationHash(): String {
    var hash = -0x340d631b8c4674c3L
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
