package org.crux.ui.screen.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.crux.data.model.IconType
import org.crux.data.model.tab.TimelineSpec
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.data.model.tab.toUiTimelineTabItem
import org.crux.data.platform.CommonTimelineSpecs
import org.crux.model.AccountType
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiList
import org.crux.ui.model.UiText
import org.crux.ui.presenter.PinTabsPresenter
import org.crux.ui.presenter.PresenterBase
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.list.AllListPresenter
import org.crux.ui.presenter.list.AllListState

public class AllListWithTabsPresenter(
    private val accountType: AccountType,
) : PresenterBase<AllListWithTabsPresenter.State>() {
    private val pinTabsPresenter by lazy {
        object : PinTabsPresenter<UiList>() {
            override fun getTimelineTabItem(item: UiList): UiTimelineTabItem =
                CommonTimelineSpecs.list
                    .candidate(
                        data = TimelineSpec.AccountResourceData(specificAccountKey(), item.id),
                        title = UiText.Raw(item.title),
                        icon =
                            (item as? UiList.List)?.avatar?.let {
                                IconType.Url(it)
                            } ?: IconType.Material(UiIcon.List),
                    ).toUiTimelineTabItem()
        }
    }

    @Composable
    override fun body(): State {
        val state =
            remember(accountType) {
                AllListPresenter(accountType)
            }.invoke()

        val pinState = pinTabsPresenter.invoke()

        return object :
            State,
            AllListState by state,
            PinTabsPresenter.State<UiList> by pinState {
        }
    }

    public interface State :
        AllListState,
        PinTabsPresenter.State<UiList>

    private fun specificAccountKey() = (accountType as AccountType.Specific).accountKey
}
