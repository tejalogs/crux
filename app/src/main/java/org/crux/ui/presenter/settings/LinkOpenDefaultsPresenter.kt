package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.crux.data.datastore.model.AppSettings
import org.crux.data.datastore.model.LinkOpenDefaultMethod
import org.crux.data.datastore.model.remove
import org.crux.data.datastore.model.upsert
import org.crux.data.repository.SettingsRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.collectAsUiState
import org.crux.ui.presenter.PresenterBase

public class LinkOpenDefaultsPresenter : PresenterBase<LinkOpenDefaultsPresenter.State>() {
    private val settingsRepository: SettingsRepository by koinInject()

    private val targetsFlow by lazy {
        settingsRepository.appSettings.map { settings ->
            settings.linkOpenDefaults.rules
                .map { rule ->
                    Target(
                        id = rule.host,
                        title = rule.host,
                        options = persistentListOf(Option.Ask, Option.Browser),
                        selectedOption =
                            when (rule.method) {
                                AppSettings.LinkOpenDefaults.Method.Browser -> Option.Browser
                                is AppSettings.LinkOpenDefaults.Method.Account -> Option.Ask
                            },
                    )
                }.toImmutableList()
        }
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val targets by targetsFlow.collectAsUiState()
        return object : State {
            override val targets: UiState<ImmutableList<Target>> = targets

            override fun select(
                target: Target,
                option: Option,
            ) {
                scope.launch {
                    withContext(Dispatchers.Main) {
                        when (option) {
                            Option.Ask -> {
                                settingsRepository.updateAppSettings {
                                    copy(linkOpenDefaults = linkOpenDefaults.remove(target.id))
                                }
                            }

                            Option.Browser -> {
                                settingsRepository.updateAppSettings {
                                    copy(linkOpenDefaults = linkOpenDefaults.upsert(target.id, LinkOpenDefaultMethod.Browser))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Immutable
    public interface State {
        public val targets: UiState<ImmutableList<Target>>

        public fun select(
            target: Target,
            option: Option,
        )
    }

    @Immutable
    public data class Target(
        val id: String,
        val title: String,
        val options: ImmutableList<Option>,
        val selectedOption: Option,
    )

    @Immutable
    public sealed interface Option {
        public val id: String
        public val isAsk: Boolean
        public val isBrowser: Boolean

        public data object Ask : Option {
            override val id: String = "ask"
            override val isAsk: Boolean = true
            override val isBrowser: Boolean = false
        }

        public data object Browser : Option {
            override val id: String = "browser"
            override val isAsk: Boolean = false
            override val isBrowser: Boolean = true
        }
    }
}
