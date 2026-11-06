package org.crux.ui.presenter.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.crux.data.repository.AccountRepository
import org.crux.di.koinInject
import org.crux.ui.model.UiState
import org.crux.ui.model.flattenUiState
import org.crux.ui.presenter.PresenterBase

public class SplashPresenter(
    private val toHome: () -> Unit,
    private val toLogin: () -> Unit,
) : PresenterBase<SplashType>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): SplashType {
        val accountState by accountRepository.activeAccount.flattenUiState()
        var type: SplashType by remember { mutableStateOf(SplashType.Splash) }
        LaunchedEffect(accountState) {
            when (accountState) {
                is UiState.Error -> {
                    delay(1000)
                    type = SplashType.Login
                    toLogin()
                }

                is UiState.Loading -> {}

                is UiState.Success -> {
                    delay(1000)
                    type = SplashType.Home
                    toHome()
                }
            }
        }
        return type
    }
}

@Immutable
public enum class SplashType {
    Splash,
    Login,
    Home,
}
