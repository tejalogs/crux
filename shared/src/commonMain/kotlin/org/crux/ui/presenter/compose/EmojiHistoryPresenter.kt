package org.crux.ui.presenter.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.crux.data.database.cache.CacheDatabase
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.accountProvider
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.ui.model.UiEmoji
import org.crux.ui.model.UiState
import org.crux.ui.model.flatMap
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.PresenterBase
import org.crux.web.shared.WebPresenter
import kotlin.time.Clock

public class EmojiHistoryPresenter(
    private val accountType: AccountType,
    private val emojis: ImmutableList<UiEmoji>,
) : PresenterBase<EmojiHistoryPresenter.State>() {
    private val cacheDatabase by koinInject<CacheDatabase>()
    private val accountRepository by koinInject<AccountRepository>()
    private val scope by koinInject<CoroutineScope>()

    @Immutable
    public interface State {
        public val history: UiState<ImmutableList<UiEmoji>>

        public fun addHistory(emoji: UiEmoji)
    }

    @Composable
    override fun body(): State {
        val accountState by accountProvider(accountType, accountRepository)
        val historyState =
            accountState.flatMap { account ->
                produceState(
                    initialValue = UiState.Loading(),
                    key1 = account,
                ) {
                    // do not use flow, call suspend function just once to avoid list flickering
                    value =
                        cacheDatabase
                            .emojiDao()
                            .getHistory(AccountType.Specific(account.accountKey))
                            .mapNotNull { history ->
                                emojis.firstOrNull { emoji -> emoji.shortcode == history.shortCode }
                            }.toImmutableList()
                            .let {
                                UiState.Success(it)
                            }
                }.value
            }
        return object : State {
            override val history = historyState

            override fun addHistory(emoji: UiEmoji) {
                accountState.onSuccess { account ->
                    scope.launch {
                        cacheDatabase.emojiDao().insertHistory(
                            org.crux.data.database.cache.model.DbEmojiHistory(
                                accountType = AccountType.Specific(account.accountKey),
                                shortCode = emoji.shortcode,
                                lastUse = Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@WebPresenter("emojiHistory")
public class WebEmojiHistoryPresenter(
    private val accountType: AccountType?,
    shortcodesText: String,
) : PresenterBase<WebEmojiHistoryPresenter.State>() {
    private val shortcodes = shortcodesText.split(SHORTCODE_SEPARATOR).filter { it.isNotEmpty() }
    private val cacheDatabase by koinInject<CacheDatabase>()
    private val accountRepository by koinInject<AccountRepository>()
    private val scope by koinInject<CoroutineScope>()

    @Immutable
    public interface State {
        public val historyShortcodes: UiState<ImmutableList<String>>

        public fun addHistory(shortcode: String)
    }

    @Composable
    override fun body(): State {
        val accountState =
            accountType?.let { type ->
                accountProvider(type, accountRepository).value
            }
        val historyState =
            accountState?.flatMap { account ->
                produceState(
                    initialValue = UiState.Loading(),
                    key1 = account,
                ) {
                    // do not use flow, call suspend function just once to avoid list flickering
                    value =
                        cacheDatabase
                            .emojiDao()
                            .getHistory(AccountType.Specific(account.accountKey))
                            .mapNotNull { history ->
                                shortcodes.firstOrNull { shortcode -> shortcode == history.shortCode }
                            }.toImmutableList()
                            .let {
                                UiState.Success(it)
                            }
                }.value
            } ?: UiState.Success(persistentListOf())
        return object : State {
            override val historyShortcodes = historyState

            override fun addHistory(shortcode: String) {
                accountState?.onSuccess { account ->
                    scope.launch {
                        cacheDatabase.emojiDao().insertHistory(
                            org.crux.data.database.cache.model.DbEmojiHistory(
                                accountType = AccountType.Specific(account.accountKey),
                                shortCode = shortcode,
                                lastUse = Clock.System.now().toEpochMilliseconds(),
                            ),
                        )
                    }
                }
            }
        }
    }

    private companion object {
        private const val SHORTCODE_SEPARATOR = "\u001f"
    }
}
