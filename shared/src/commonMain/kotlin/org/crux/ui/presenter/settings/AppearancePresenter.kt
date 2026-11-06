package org.crux.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import org.crux.ui.model.UiState
import org.crux.ui.model.UiTimelineV2
import org.crux.ui.model.createSampleStatus
import org.crux.ui.model.map
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.home.ActiveAccountPresenter
import org.crux.web.shared.WebPresenter

@WebPresenter("appearance")
public class AppearancePresenter : PresenterBase<AppearanceState>() {
    @Composable
    override fun body(): AppearanceState {
        val account =
            remember {
                ActiveAccountPresenter()
            }.body()
        return object : AppearanceState {
            override val sampleStatus =
                account.user.map {
                    createSampleStatus(it)
                }
        }
    }
}

@Immutable
public interface AppearanceState {
    public val sampleStatus: UiState<UiTimelineV2.Post>
}
