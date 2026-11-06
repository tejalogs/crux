package org.crux.ui.presenter.compose

import org.crux.data.database.app.model.DraftContent
import org.crux.data.database.app.model.DraftReferenceType
import org.crux.data.repository.ComposeDraftBundle
import org.crux.data.repository.DraftMediaStore
import org.crux.data.repository.DraftRepository
import org.crux.data.repository.SaveDraftInput
import org.crux.data.repository.SaveDraftTarget
import org.koin.core.annotation.Single

@Single
internal class SaveDraftUseCase(
    private val draftRepository: DraftRepository,
    private val draftMediaStore: DraftMediaStore,
) {
    suspend operator fun invoke(bundle: ComposeDraftBundle): String {
        val persistedMedia = draftMediaStore.persist(groupId = bundle.groupId, medias = bundle.template.medias)
        return draftRepository.saveDraft(
            SaveDraftInput(
                groupId = bundle.groupId,
                content = bundle.template.toDraftContent(),
                targets =
                    bundle.accounts.map {
                        SaveDraftTarget(
                            accountKey = it.accountKey,
                        )
                    },
                medias = persistedMedia,
            ),
        )
    }
}

internal fun org.crux.data.datasource.microblog.ComposeData.toDraftContent(): DraftContent =
    DraftContent(
        text = content,
        visibility = visibility,
        language = language,
        sensitive = sensitive,
        spoilerText = spoilerText,
        localOnly = localOnly,
        poll =
            poll?.let {
                DraftContent.DraftPoll(
                    options = it.options,
                    expiredAfter = it.expiredAfter,
                    multiple = it.multiple,
                )
            },
        reference =
            referenceStatus?.let {
                DraftContent.DraftReference(
                    type =
                        when (it.composeStatus) {
                            is ComposeStatus.Quote -> DraftReferenceType.QUOTE
                            is ComposeStatus.Reply -> DraftReferenceType.REPLY
                        },
                    statusKey = it.composeStatus.statusKey,
                )
            },
    )
