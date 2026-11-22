package org.crux.ui.screen.rss

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.ui.model.UiState
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.home.rss.ImportOPMLPresenter
import org.crux.ui.presenter.home.rss.countImportableOpmlFeeds
import org.crux.ui.theme.screenHorizontalPadding
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Imports the feeds in an OPML file. The file can come from another app (share or VIEW intent), so
 * nothing is subscribed until the user has seen how many feeds it holds and tapped Import.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OPMLImportSheet(
    uri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preview by producePresenter("opml_preview_$uri") {
        previewPresenter(uri, context)
    }
    var confirmed by rememberSaveable(uri) { mutableStateOf(false) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProvideTextStyle(
            MaterialTheme.typography.titleMedium,
        ) {
            Text(
                stringResource(R.string.opml_import),
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
        }
        preview
            .onLoading {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    )
                }
            }.onError {
                Text(text = it.message ?: "Unknown error")
            }.onSuccess { opml ->
                if (confirmed) {
                    OpmlImportProgress(content = opml.content, uri = uri, onBack = onBack)
                } else {
                    OpmlImportConfirmation(
                        feedCount = opml.feedCount,
                        onImport = { confirmed = true },
                        onCancel = onBack,
                    )
                }
            }
    }
}

@Composable
private fun OpmlImportConfirmation(
    feedCount: Int,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
    ) {
        Text(
            text =
                if (feedCount > 0) {
                    stringResource(R.string.opml_import_confirm_message, feedCount)
                } else {
                    stringResource(R.string.opml_import_no_feeds)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            if (feedCount > 0) {
                Button(onClick = onImport) {
                    Text(stringResource(R.string.opml_import_confirm_action))
                }
            }
        }
    }
}

@Composable
private fun OpmlImportProgress(
    content: String,
    uri: Uri,
    onBack: () -> Unit,
) {
    val state by producePresenter("import_rss_$uri") {
        remember(content) { ImportOPMLPresenter(content) }.body()
    }
    Column {
        ImportOPMLContent(
            state = state,
            onGoBack = onBack,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                onBack.invoke()
            },
            enabled = !state.importing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = screenHorizontalPadding,
                        vertical = 8.dp,
                    ),
        ) {
            Text(stringResource(android.R.string.ok))
        }
    }
}

private const val MAX_OPML_IMPORT_BYTES = 5 * 1024 * 1024

internal fun readBoundedOpmlText(
    context: Context,
    uri: Uri,
    maxBytes: Int = MAX_OPML_IMPORT_BYTES,
): String {
    if (!uri.scheme.equals("content", ignoreCase = true)) {
        throw SecurityException("Only content:// OPML documents are supported.")
    }
    return context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IOException("OPML file exceeds maximum allowed size (${maxBytes / (1024 * 1024)} MB).")
            }
            out.write(buffer, 0, read)
        }
        out.toString(Charsets.UTF_8.name())
    } ?: ""
}

/** The OPML text read from the file, plus how many feeds it would subscribe to. */
private data class OpmlPreview(
    val content: String,
    val feedCount: Int,
)

@Composable
private fun previewPresenter(
    uri: Uri,
    context: Context,
): UiState<OpmlPreview> {
    var previewState by remember { mutableStateOf<UiState<OpmlPreview>>(UiState.Loading()) }

    LaunchedEffect(uri, context) {
        previewState =
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        readBoundedOpmlText(context, uri)
                    }
                if (content.isEmpty()) {
                    UiState.Error(Exception("Empty content"))
                } else {
                    val feedCount = withContext(Dispatchers.Default) { countImportableOpmlFeeds(content) }
                    UiState.Success(OpmlPreview(content = content, feedCount = feedCount))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    return previewState
}
