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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.crux.R
import org.crux.rss.OpmlCodec
import org.crux.rss.RssRepository
import org.crux.ui.model.UiState
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
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
    var preview by remember(uri) { mutableStateOf<UiState<OpmlPreview>>(UiState.Loading()) }
    var confirmed by rememberSaveable(uri) { mutableStateOf(false) }

    LaunchedEffect(uri, context) {
        preview =
            try {
                val content = withContext(Dispatchers.IO) { readBoundedOpmlText(context, uri) }
                if (content.isEmpty()) {
                    UiState.Error(Exception("Empty content"))
                } else {
                    val feedCount = withContext(Dispatchers.Default) { OpmlCodec.countImportableFeeds(content) }
                    UiState.Success(OpmlPreview(content = content, feedCount = feedCount))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Text(
                stringResource(R.string.opml_import),
                modifier = Modifier.padding(horizontal = screenHorizontalPadding),
            )
        }
        preview
            .onLoading {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.padding(horizontal = screenHorizontalPadding),
                    )
                }
            }.onError {
                Text(
                    text = it.message ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
                )
            }.onSuccess { opml ->
                if (confirmed) {
                    OpmlImportProgress(content = opml.content, totalExpected = opml.feedCount, onBack = onBack)
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
    totalExpected: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember(context) { RssRepository.get(context) }
    var importing by remember { mutableStateOf(true) }
    var importedCount by remember { mutableIntStateOf(0) }
    var totalCount by remember { mutableIntStateOf(totalExpected) }
    var currentTitle by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(content) {
        importing = true
        try {
            importedCount =
                repo.importOpml(content) { imported, total, title ->
                    importedCount = imported
                    totalCount = total
                    currentTitle = title
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message ?: "Import failed"
        } finally {
            importing = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = screenHorizontalPadding, vertical = 8.dp),
    ) {
        val progress = if (totalCount > 0) importedCount.toFloat() / totalCount.toFloat() else 1f
        if (importing) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Importing $importedCount / $totalCount: $currentTitle",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = "Imported $importedCount feeds into Crux. Articles are syncing in the background.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onBack,
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
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

private data class OpmlPreview(
    val content: String,
    val feedCount: Int,
)
