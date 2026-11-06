package org.crux.ui.presenter.compose

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.UiAccount
import org.crux.ui.model.UiState
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAccountSelectionFlowTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `account updates do not reemit unchanged account map`() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "example.com")
            val account =
                accountKey to
                    UiState.Success(
                        UiAccount(
                            accountKey = accountKey,
                            platformId = "Mastodon",
                        ),
                    )
            val accountUpdates = MutableSharedFlow<Pair<MicroBlogKey, UiState<UiAccount>>>()
            val emissions = mutableListOf<Map<MicroBlogKey, UiState<UiAccount>>>()
            val collection =
                observeAllComposeAccounts(
                    accountFlows = flowOf(listOf(accountUpdates)),
                ).onEach(emissions::add)
                    .launchIn(backgroundScope)

            runCurrent()
            accountUpdates.emit(account)
            runCurrent()
            accountUpdates.emit(account)
            runCurrent()

            val expectedEmissions: List<Map<MicroBlogKey, UiState<UiAccount>>> =
                listOf(mapOf(account))
            assertEquals(expectedEmissions, emissions)
            collection.cancel()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `account updates do not reemit unchanged selected keys`() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "example.com")
            val accountState =
                mapOf(
                    accountKey to
                        UiState.Success(
                            UiAccount(
                                accountKey = accountKey,
                                platformId = "Mastodon",
                            ),
                        ),
                )
            val allAccountsFlow = MutableSharedFlow<Map<MicroBlogKey, UiState<UiAccount>>>()
            val selectedAccountsKeyFlow = MutableStateFlow(persistentListOf(accountKey))
            val emissions = mutableListOf<ImmutableList<MicroBlogKey>>()
            val collection =
                observeSelectedComposeAccountKeys(
                    allAccountsFlow = allAccountsFlow,
                    selectedAccountsKeyFlow = selectedAccountsKeyFlow,
                ).onEach(emissions::add)
                    .launchIn(backgroundScope)

            runCurrent()
            allAccountsFlow.emit(accountState)
            runCurrent()
            allAccountsFlow.emit(accountState)
            runCurrent()

            val expectedEmissions: List<ImmutableList<MicroBlogKey>> =
                listOf(persistentListOf(accountKey))
            assertEquals(expectedEmissions, emissions)
            collection.cancel()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `selecting fallback account does not restart status load`() =
        runTest {
            val accountKey = MicroBlogKey(id = "account", host = "example.com")
            val status = ComposeStatus.Reply(MicroBlogKey(id = "status", host = "example.com"))
            val activeStatusFlow = MutableStateFlow<ComposeStatus?>(status)
            val selectedAccountKeysFlow =
                MutableStateFlow<ImmutableList<MicroBlogKey>>(persistentListOf())
            val emissions = mutableListOf<Pair<ComposeStatus?, AccountType.Specific?>>()
            val collection =
                observeComposeStatusTarget(
                    activeStatusFlow = activeStatusFlow,
                    selectedAccountKeysFlow = selectedAccountKeysFlow,
                    fallbackAccountType = AccountType.Specific(accountKey),
                ).onEach(emissions::add)
                    .launchIn(backgroundScope)

            runCurrent()
            selectedAccountKeysFlow.value = persistentListOf(accountKey)
            runCurrent()

            val expectedEmissions: List<Pair<ComposeStatus?, AccountType.Specific?>> =
                listOf(status to AccountType.Specific(accountKey))
            assertEquals(expectedEmissions, emissions)
            collection.cancel()
        }

    @Test
    fun `resolveInitialSelectedAccounts selects specific account when available`() {
        val accountKey1 = MicroBlogKey(id = "user1", host = "x.com")
        val accountKey2 = MicroBlogKey(id = "user2", host = "linkedin.com")
        val accounts =
            mapOf(
                accountKey1 to "account1",
                accountKey2 to "account2",
            )
        val selected =
            resolveInitialSelectedAccounts(
                composeAccounts = accounts,
                lastAccounts = emptyList(),
                specificAccountKey = accountKey2,
            )
        assertEquals(persistentListOf(accountKey2), selected)
    }

    @Test
    fun `resolveInitialSelectedAccounts selects last used accounts when matching`() {
        val accountKey1 = MicroBlogKey(id = "user1", host = "x.com")
        val accountKey2 = MicroBlogKey(id = "user2", host = "linkedin.com")
        val accounts =
            mapOf(
                accountKey1 to "account1",
                accountKey2 to "account2",
            )
        val selected =
            resolveInitialSelectedAccounts(
                composeAccounts = accounts,
                lastAccounts = listOf(accountKey2),
            )
        assertEquals(persistentListOf(accountKey2), selected)
    }

    @Test
    fun `resolveInitialSelectedAccounts falls back to first account when last accounts are empty or missing`() {
        val accountKey1 = MicroBlogKey(id = "user1", host = "x.com")
        val accountKey2 = MicroBlogKey(id = "user2", host = "linkedin.com")
        val accounts =
            mapOf(
                accountKey1 to "account1",
                accountKey2 to "account2",
            )
        val selected =
            resolveInitialSelectedAccounts(
                composeAccounts = accounts,
                lastAccounts = emptyList(),
            )
        assertEquals(persistentListOf(accountKey1), selected)
    }

    @Test
    fun `resolveInitialSelectedAccounts returns empty when accounts are empty`() {
        val selected =
            resolveInitialSelectedAccounts(
                composeAccounts = emptyMap<MicroBlogKey, Any>(),
                lastAccounts = emptyList(),
            )
        assertEquals(persistentListOf(), selected)
    }
}
