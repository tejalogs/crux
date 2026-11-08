package org.crux.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.crux.R
import org.crux.ui.component.BackButton
import org.crux.ui.component.CruxLargeFlexibleTopAppBar
import org.crux.ui.component.CruxScaffold
import org.crux.ui.component.listCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val libraries by produceState<List<OpenSourceLibrary>?>(initialValue = null, context) {
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    context.resources
                        .openRawResource(R.raw.aboutlibraries)
                        .bufferedReader()
                        .use { parseOpenSourceLibraries(it.readText()) }
                }.getOrDefault(emptyList())
            }
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    CruxScaffold(
        topBar = {
            CruxLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_about_open_source_licenses))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = topAppBarScrollBehavior,
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        val items = libraries.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    text =
                        if (libraries != null && items.isEmpty()) {
                            stringResource(id = R.string.settings_licenses_unavailable)
                        } else {
                            stringResource(id = R.string.settings_licenses_intro, items.size)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            itemsIndexed(items, key = { _, library -> library.id }) { index, library ->
                val licenseText =
                    library.licenses.takeIf { it.isNotEmpty() }?.joinToString(", ")
                        ?: stringResource(id = R.string.settings_licenses_not_declared)
                ListItem(
                    modifier =
                        Modifier
                            .listCard(index = index, totalCount = items.size)
                            .then(
                                library.website?.let { site -> Modifier.clickable { uriHandler.openUri(site) } } ?: Modifier,
                            ),
                    overlineContent = {
                        if (library.version.isNotBlank()) Text(text = library.version)
                    },
                    headlineContent = { Text(text = library.name) },
                    supportingContent = { Text(text = licenseText) },
                )
            }
        }
    }
}
