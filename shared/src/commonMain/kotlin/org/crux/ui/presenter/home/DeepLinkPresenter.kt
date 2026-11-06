package org.crux.ui.presenter.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.crux.common.deeplink.PlatformDeepLinkMatcher
import org.crux.data.database.cache.model.TranslationDisplayMode
import org.crux.data.datasource.microblog.datasource.PostDataSource
import org.crux.data.datastore.model.LinkOpenDefaultMethod
import org.crux.data.datastore.model.methodFor
import org.crux.data.repository.AccountRepository
import org.crux.data.repository.SettingsRepository
import org.crux.data.repository.accountServiceFlow
import org.crux.data.translation.PreTranslationService
import org.crux.di.koinInject
import org.crux.model.AccountType
import org.crux.model.PlatformRegistry
import org.crux.ui.model.DeeplinkEvent
import org.crux.ui.model.UiAccount
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.route.DeeplinkRoute

public class DeepLinkPresenter(
    private val onRoute: (DeeplinkRoute) -> Unit,
    private val onLink: (String) -> Unit,
) : PresenterBase<DeepLinkPresenter.State>() {
    private val accountRepository: AccountRepository by koinInject()
    private val preTranslationService: PreTranslationService by koinInject()
    private val platformRegistry: PlatformRegistry by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()

    @androidx.compose.runtime.Immutable
    public interface State {
        public fun handle(url: String)
    }

    private val deepLinkFlow by lazy {
        accountRepository.allAccounts.map {
            it
                .mapNotNull { account ->
                    platformRegistry
                        .get(account.platformId)
                        ?.let { spec -> account to spec.deepLinks(account.accountKey) }
                }.toMap()
                .toImmutableMap()
        }
    }

    @Composable
    override fun body(): State {
        var pendingUrl by remember { mutableStateOf<String?>(null) }
        pendingUrl?.let { url ->
            LaunchedEffect(url) {
                if (DeeplinkEvent.isDeeplinkEvent(url)) {
                    val event = DeeplinkEvent.parse(url)
                    if (event != null) {
                        when {
                            event.postEvent != null -> {
                                accountServiceFlow(
                                    accountType = AccountType.Specific(event.accountKey),
                                    repository = accountRepository,
                                ).firstOrNull()?.let { service ->
                                    if (service is PostDataSource) {
                                        service.postEventHandler.handleEvent(event.postEvent)
                                    }
                                }
                            }

                            event.translationEvent is DeeplinkEvent.TranslationEvent.RetryTranslation -> {
                                with(event.translationEvent) {
                                    preTranslationService.setStatusDisplayMode(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                        mode = TranslationDisplayMode.Translated,
                                    )
                                    preTranslationService.retryStatus(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                    )
                                }
                            }

                            event.translationEvent is DeeplinkEvent.TranslationEvent.Translate -> {
                                with(event.translationEvent) {
                                    preTranslationService.setStatusDisplayMode(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                        mode = TranslationDisplayMode.Translated,
                                    )
                                    preTranslationService.retryStatus(
                                        accountType = AccountType.Specific(event.accountKey),
                                        statusKey = statusKey,
                                    )
                                }
                            }

                            event.translationEvent is DeeplinkEvent.TranslationEvent.ShowOriginal -> {
                                preTranslationService.setStatusDisplayMode(
                                    accountType = AccountType.Specific(event.accountKey),
                                    statusKey = event.translationEvent.statusKey,
                                    mode = TranslationDisplayMode.Original,
                                )
                            }
                        }
                    }
                    pendingUrl = null
                } else if (DeeplinkRoute.isDeeplink(url)) {
                    DeeplinkRoute.parse(url)?.let {
                        when (it) {
                            is DeeplinkRoute.OpenLinkDirectly -> {
                                withContext(Dispatchers.Main) {
                                    onLink(it.url)
                                }
                            }

                            else -> {
                                withContext(Dispatchers.Main) {
                                    onRoute(it)
                                }
                            }
                        }
                    }
                    pendingUrl = null
                } else {
                    val deepLinks = deepLinkFlow.firstOrNull().orEmpty()
                    val matches = PlatformDeepLinkMatcher.matches(url, deepLinks)
                    if (matches.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            onLink.invoke(url)
                        }
                    } else {
                        val defaultHost = matches.defaultHost()
                        val defaultMethod =
                            defaultHost?.let { host ->
                                settingsRepository
                                    .appSettings
                                    .firstOrNull()
                                    ?.linkOpenDefaults
                                    ?.methodFor(host)
                            }
                        val defaultRoute =
                            when (defaultMethod) {
                                LinkOpenDefaultMethod.Browser -> {
                                    withContext(Dispatchers.Main) {
                                        onLink.invoke(url)
                                    }
                                    null
                                }

                                is LinkOpenDefaultMethod.Account -> {
                                    matches.entries
                                        .firstOrNull { it.key.accountKey == defaultMethod.accountKey }
                                        ?.value
                                        ?.route
                                }

                                null -> {
                                    null
                                }
                            }
                        if (defaultRoute != null) {
                            withContext(Dispatchers.Main) {
                                onRoute.invoke(defaultRoute)
                            }
                        } else if (defaultMethod !is LinkOpenDefaultMethod.Browser) {
                            val route =
                                DeeplinkRoute.DeepLinkAccountPicker(
                                    originalUrl = url,
                                    data =
                                        matches
                                            .map {
                                                it.key.accountKey to it.value.route
                                            }.toMap()
                                            .toImmutableMap(),
                                )
                            withContext(Dispatchers.Main) {
                                onRoute.invoke(route)
                            }
                        }
                    }
                    pendingUrl = null
                }
            }
        }

        return object : State {
            override fun handle(url: String) {
                pendingUrl = url
            }
        }
    }
}

private fun Map<UiAccount, PlatformDeepLinkMatcher.Match>.defaultHost(): String? {
    val hosts = values.map { it.host }.distinct()
    if (hosts.size != 1) return null
    return hosts.single()
}
