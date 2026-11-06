package org.crux.data.repository

import okio.Path.Companion.toPath
import org.crux.common.FileItem
import org.crux.common.FileType
import org.crux.common.MediaFileNamePolicy
import org.crux.data.database.app.model.DraftMediaType
import org.crux.data.datasource.microblog.ComposeData
import org.crux.data.io.FileStorage
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal class DraftMediaStore(
    private val fileStorage: FileStorage,
) {
    suspend fun persist(
        groupId: String,
        medias: List<ComposeData.Media>,
    ): List<SaveDraftMedia> {
        val persisted =
            medias.mapIndexed { index, media ->
                val fileName =
                    media.file.name
                        ?.let { MediaFileNamePolicy.sanitizeFileName(it, fallback = "") }
                        .orEmpty()
                        .ifBlank { "${Uuid.random()}.bin" }
                val path = fileStorage.draftMediaFile(groupId, "${index}_$fileName")
                fileStorage.createDirectories(checkNotNull(path.parent))
                fileStorage.write(path, media.file.uploadMedia())
                SaveDraftMedia(
                    cachePath = path.toString(),
                    fileName = media.file.name,
                    mediaType = media.file.type.toDraftMediaType(),
                    altText = media.altText,
                    sortOrder = index,
                )
            }
        cleanupStaleFiles(
            groupId = groupId,
            keepPaths = persisted.map { it.cachePath.toPath() }.toSet(),
        )
        return persisted
    }

    suspend fun restore(medias: List<DraftMedia>): List<ComposeData.Media> =
        medias.map { media ->
            ComposeData.Media(
                file =
                    draftFileItem(
                        path = media.cachePath,
                        name = media.fileName,
                        type = media.mediaType.toFileType(),
                        loader = {
                            fileStorage.read(media.cachePath.toPath())
                        },
                    ),
                altText = media.altText,
            )
        }

    fun delete(medias: List<DraftMedia>) {
        medias.forEach { media ->
            val path = media.cachePath.toPath()
            if (fileStorage.exists(path)) {
                fileStorage.delete(path)
            }
            cleanupEmptyGroupDirectory(media.groupId)
        }
    }

    private fun cleanupStaleFiles(
        groupId: String,
        keepPaths: Set<okio.Path>,
    ) {
        val groupDirectory =
            fileStorage
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileStorage.exists(groupDirectory)) {
            return
        }
        fileStorage
            .list(groupDirectory)
            .filterNot { keepPaths.contains(it) }
            .forEach { stalePath ->
                if (fileStorage.exists(stalePath)) {
                    fileStorage.delete(stalePath)
                }
            }
        cleanupEmptyGroupDirectory(groupId)
    }

    private fun cleanupEmptyGroupDirectory(groupId: String) {
        val groupDirectory =
            fileStorage
                .draftMediaFile(groupId, "__placeholder__")
                .parent ?: return
        if (!fileStorage.exists(groupDirectory)) {
            return
        }
        if (fileStorage.list(groupDirectory).isEmpty()) {
            fileStorage.delete(groupDirectory)
        }
    }
}

internal expect fun draftFileItem(
    path: String,
    name: String?,
    type: FileType,
    mimeType: String? = null,
    loader: suspend () -> ByteArray,
): FileItem

private fun FileType.toDraftMediaType(): DraftMediaType =
    when (this) {
        FileType.Image -> DraftMediaType.IMAGE
        FileType.Video -> DraftMediaType.VIDEO
        FileType.Other -> DraftMediaType.OTHER
    }

private fun DraftMediaType.toFileType(): FileType =
    when (this) {
        DraftMediaType.IMAGE -> FileType.Image
        DraftMediaType.VIDEO -> FileType.Video
        DraftMediaType.OTHER -> FileType.Other
    }
