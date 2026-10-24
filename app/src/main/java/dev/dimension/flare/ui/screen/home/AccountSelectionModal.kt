package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Instagram
import compose.icons.fontawesomeicons.solid.ArrowUpRightFromSquare
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.settings.AccountsPresenter
import dev.dimension.flare.ui.route.Route
import dev.dimension.flare.ui.screen.settings.AccountItem
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import dev.dimension.flare.ui.theme.segmentedShapes2
import moe.tlaster.precompose.molecule.producePresenter

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
        state.accounts.onSuccess {
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
                        toLogin = {
                            navigate(Route.ServiceSelect.Selection)
                        },
                        toRelogin = {
                            navigate(Route.ServiceSelect.Relogin(it))
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
        OutlinedCard(
            onClick = {
                onBack.invoke()
                navigate(Route.InstagramWeb())
            },
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFF833AB4),
                                            Color(0xFFFD1D1D),
                                            Color(0xFFFCB045),
                                        ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        imageVector = FontAwesomeIcons.Brands.Instagram,
                        contentDescription = "Instagram",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Instagram (Web)",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Sandboxed session with ad & privacy shield",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.ArrowUpRightFromSquare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Button(
            onClick = {
                onBack.invoke()
                navigate(Route.ServiceSelect.Selection)
            },
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.quick_menu_add_account))
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
