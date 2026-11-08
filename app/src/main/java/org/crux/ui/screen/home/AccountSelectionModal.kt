package org.crux.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.data.platform.INSTAGRAM_PLATFORM_ID
import org.crux.data.platform.LINKEDIN_PLATFORM_ID
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.invoke
import org.crux.ui.presenter.settings.AccountsPresenter
import org.crux.ui.route.Route
import org.crux.ui.route.reloginRoute
import org.crux.ui.screen.settings.AccountItem
import org.crux.ui.theme.screenHorizontalPadding
import org.crux.ui.theme.segmentedShapes2

@Composable
internal fun AccountSelectionModal(
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = screenHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val state by producePresenter { presenter() }
        Text(
            text = stringResource(R.string.crux_switch_account_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        state.accounts.onSuccess { all ->
            // Instagram and LinkedIn are browser sessions managed as Crux platforms; listing them
            // here as well is what made this sheet show every account twice.
            val it =
                all.filter { item ->
                    item.account.platformId != INSTAGRAM_PLATFORM_ID &&
                        item.account.platformId != LINKEDIN_PLATFORM_ID
                }
            if (it.isEmpty()) {
                Text(
                    text = stringResource(R.string.crux_switch_account_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                for (index in 0 until it.size) {
                    val (accountKey, data) = it[index]
                    AccountItem(
                        userState = data,
                        shapes = ListItemDefaults.segmentedShapes2(index, it.size),
                        onClick = {
                            state.setActiveAccount(it)
                            onBack.invoke()
                        },
                        toRelogin = {
                            navigate(reloginRoute(it.platformId))
                        },
                        trailingContent = { user ->
                            state.activeAccount.onSuccess {
                                RadioButton(
                                    selected = it.accountKey == user.key,
                                    onClick = {
                                        state.setActiveAccount(
                                            user.key,
                                        )
                                        onBack.invoke()
                                    },
                                )
                            }
                        },
                    )
                }
            }
        }
        Button(
            onClick = {
                onBack.invoke()
                navigate(Route.Settings.Accounts)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Text(text = stringResource(R.string.crux_manage_platforms))
        }
    }
}

@Composable
private fun presenter() =
    run {
        remember {
            AccountsPresenter()
        }.invoke()
    }
