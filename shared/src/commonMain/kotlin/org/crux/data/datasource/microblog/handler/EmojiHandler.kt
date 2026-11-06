package org.crux.data.datasource.microblog.handler

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transform
import kotlinx.serialization.SerializationException
import org.crux.common.Cacheable
import org.crux.common.decodeProtobuf
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.database.cache.model.DbEmoji
import org.crux.data.database.cache.model.EmojiContent
import org.crux.data.datasource.microblog.loader.EmojiLoader
import org.crux.di.koinInject
import org.crux.ui.model.UiEmoji
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class EmojiHandler(
    private val host: String,
    private val loader: EmojiLoader,
) {
    private val database: CacheDatabase by koinInject()
    public val emoji: Cacheable<ImmutableMap<String, ImmutableList<UiEmoji>>> =
        Cacheable(
            fetchSource = {
                val emojis = loader.emojis()
                database.emojiDao().insert(
                    DbEmoji(
                        host = host,
                        content = EmojiContent(emojis),
                    ),
                )
            },
            cacheSource = {
                database
                    .emojiDao()
                    .getContent(host)
                    .distinctUntilChanged()
                    .transform { content ->
                        if (content == null) {
                            return@transform
                        }
                        val decoded =
                            runCatching {
                                if (content.isEmpty()) {
                                    EmojiContent()
                                } else {
                                    content.decodeProtobuf<EmojiContent>()
                                }
                            }.getOrElse { throwable ->
                                if (throwable is SerializationException) {
                                    database.emojiDao().delete(host)
                                    return@transform
                                }
                                throw throwable
                            }
                        emit(decoded.data)
                    }
            },
        )
}
