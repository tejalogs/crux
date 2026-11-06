package org.crux.ui.presenter.compose

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull
import org.crux.data.database.app.model.DraftContent
import org.crux.data.database.app.model.DraftMediaType
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.DraftRepository
import org.crux.ui.model.UiDraft
import org.crux.ui.model.UiDraftAccount
import org.crux.ui.model.UiDraftMedia
import org.crux.ui.model.UiDraftMediaType
import org.crux.ui.model.UiDraftStatus
import org.crux.ui.render.toUi
import org.koin.core.annotation.Single
import kotlin.time.Instant

@Single
public class RestoreDraftUseCase internal constructor(
    private val draftRepository: DraftRepository,
    private val accountRepository: AccountRepository,
) {
    public suspend operator fun invoke(groupId: String): UiDraft? {
        val draft = draftRepository.draft(groupId).firstOrNull() ?: return null
        val accounts =
            draft.targets.mapNotNull { target ->
                accountRepository.find(target.accountKey)?.let {
                    UiDraftAccount(account = it)
                }
            }
        return UiDraft(
            groupId = draft.groupId,
            status = draft.toUiDraftStatus(),
            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt).toUi(),
            accounts = accounts.toImmutableList(),
            data = draft.content.toComposeData(medias = emptyList()),
            medias =
                draft.medias
                    .map { media ->
                        UiDraftMedia(
                            cachePath = media.cachePath,
                            fileName = media.fileName,
                            type =
                                when (media.mediaType) {
                                    DraftMediaType.IMAGE -> UiDraftMediaType.IMAGE
                                    DraftMediaType.VIDEO -> UiDraftMediaType.VIDEO
                                    DraftMediaType.OTHER -> UiDraftMediaType.OTHER
                                },
                            altText = media.altText,
                        )
                    }.toImmutableList(),
        )
    }
}

internal fun DraftContent.toComposeData(
    medias: List<org.crux.data.datasource.microblog.ComposeData.Media>,
): org.crux.data.datasource.microblog.ComposeData =
    org.crux.data.datasource.microblog.ComposeData(
        content = text,
        visibility = visibility,
        language = language,
        medias = medias,
        sensitive = sensitive,
        spoilerText = spoilerText,
        poll =
            poll?.let {
                org.crux.data.datasource.microblog.ComposeData.Poll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        localOnly = localOnly,
        referenceStatus =
            reference?.let { reference ->
                org.crux.data.datasource.microblog.ComposeData.ReferenceStatus(
                    composeStatus = reference.toComposeStatus(),
                )
            },
    )

internal fun DraftContent.DraftReference.toComposeStatus(): ComposeStatus =
    when (type) {
        org.crux.data.database.app.model.DraftReferenceType.QUOTE -> {
            ComposeStatus.Quote(statusKey)
        }

        // VVO_COMMENT only exists for drafts saved by older builds; restore it as a plain reply.
        org.crux.data.database.app.model.DraftReferenceType.REPLY,
        org.crux.data.database.app.model.DraftReferenceType.VVO_COMMENT,
        -> {
            ComposeStatus.Reply(statusKey)
        }
    }

private fun org.crux.data.repository.DraftGroup.toUiDraftStatus(): UiDraftStatus =
    when {
        targets.any { it.status == org.crux.data.database.app.model.DraftTargetStatus.SENDING } -> UiDraftStatus.SENDING
        targets.any { it.status == org.crux.data.database.app.model.DraftTargetStatus.FAILED } -> UiDraftStatus.FAILED
        else -> UiDraftStatus.DRAFT
    }
