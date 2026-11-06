package org.crux.ui.presenter.home.rss

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.crux.data.repository.SubscriptionSourceInput
import org.crux.di.startKoin
import org.crux.ui.model.UiState
import org.crux.ui.model.isSuccess
import org.crux.ui.model.takeSuccess
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ExportOPMLPresenterTest {
    private lateinit var subscriptionRepository: FakeSubscriptionRepository

    @BeforeTest
    fun setup() {
        subscriptionRepository = startKoin().koin.get()
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testExportOPML() =
        runTest {
            // Given
            val source1 =
                SubscriptionSourceInput(
                    url = "https://example.com/feed1",
                    title = "Feed 1",
                    icon = null,
                    lastUpdateMillis = 0,
                )
            val source2 =
                SubscriptionSourceInput(
                    url = "https://example.com/feed2",
                    title = "Feed 2",
                    icon = null,
                    lastUpdateMillis = 0,
                )
            subscriptionRepository.upsertAll(listOf(source1, source2))

            val presenter = ExportOPMLPresenter()

            val states = mutableListOf<UiState<String>>()
            val job =
                launch {
                    moleculeFlow(mode = RecompositionMode.Immediate) {
                        presenter.body()
                    }.collect {
                        states.add(it)
                    }
                }

            advanceUntilIdle()
            job.cancel()

            val finalState = states.last()

            assertTrue(finalState.isSuccess)
            val opml = finalState.takeSuccess()
            assertNotNull(opml)

            assertContains(opml, "Feed 1")
            assertContains(opml, "https://example.com/feed1")
            assertContains(opml, "Feed 2")
            assertContains(opml, "https://example.com/feed2")
        }
}
