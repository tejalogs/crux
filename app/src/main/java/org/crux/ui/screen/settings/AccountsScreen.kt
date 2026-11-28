package org.crux.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.route.Route
import org.crux.ui.screen.crux.CruxPlatformList
import org.crux.ui.screen.crux.CruxSectionHeader
import org.crux.ui.screen.crux.rememberCruxConnections
import org.crux.ui.theme.screenHorizontalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val connections = rememberCruxConnections()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        connections.refresh()
    }
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_accounts_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item(key = "crux_platforms_header") {
                CruxSectionHeader(
                    title = stringResource(R.string.crux_accounts_platforms_title),
                    subtitle = stringResource(R.string.crux_accounts_platforms_subtitle),
                )
            }
            item(key = "crux_platforms") {
                CruxPlatformList(
                    connections = connections,
                    navigate = navigate,
                    snackbarHostState = snackbarHostState,
                    snackbarScope = screenScope,
                )
            }
        }
    }
}
