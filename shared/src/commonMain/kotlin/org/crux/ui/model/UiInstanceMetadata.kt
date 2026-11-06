package org.crux.ui.model

import androidx.compose.runtime.Immutable
import org.crux.common.SerializableImmutableList
import org.crux.common.SerializableImmutableMap

@Immutable
public data class UiInstanceMetadata(
    val instance: UiInstance,
    val rules: SerializableImmutableMap<String, String>,
    val configuration: Configuration,
) {
    @Immutable
    public data class Configuration(
        val registration: Registration,
        val statuses: Statuses,
        val mediaAttachment: MediaAttachment,
        val poll: Poll,
    ) {
        @Immutable
        public data class Statuses(
            val maxCharacters: Long,
            val maxMediaAttachments: Long,
        )

        @Immutable
        public data class Registration(
            val enabled: Boolean,
        )

        @Immutable
        public data class MediaAttachment(
            val imageSizeLimit: Long,
            val descriptionLimit: Long,
            val supportedMimeTypes: SerializableImmutableList<String>,
        )

        @Immutable
        public data class Poll(
            val maxOptions: Long,
            val maxCharactersPerOption: Long,
            val minExpiration: Long,
            val maxExpiration: Long,
        )
    }
}
