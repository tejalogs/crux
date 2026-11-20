package org.crux.ui.screen.rss

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.R
import org.crux.ui.model.UiState
import org.crux.ui.model.map
import org.crux.ui.model.onError
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.home.rss.ImportOPMLPresenter
import org.crux.ui.theme.screenHorizontalPadding
import java.io.ByteArrayOutputStream
import java.io.IOException

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OPMLImportSheet(
    uri: Uri,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by producePresenter("import_rss_$uri") {
        presenter(uri, context)
    }
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
        state
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
            }.onSuccess { state ->
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

@Composable
private fun presenter(
    uri: Uri,
    context: Context,
) = run {
    var contentState by remember { mutableStateOf<UiState<String>>(UiState.Loading()) }

    LaunchedEffect(uri, context) {
        contentState =
            try {
                val content =
                    withContext(Dispatchers.IO) {
                        readBoundedOpmlText(context, uri)
                    }
                if (content.isEmpty()) {
                    UiState.Error(Exception("Empty content"))
                } else {
                    UiState.Success(content)
                }
            } catch (e: Exception) {
                UiState.Error(e)
            }
    }

    contentState.map {
        remember(it) { ImportOPMLPresenter(it) }.body()
    }
}
