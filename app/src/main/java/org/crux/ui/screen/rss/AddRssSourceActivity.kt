package org.crux.ui.screen.rss

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import org.crux.ui.CruxApp

class AddRssSourceActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        val streamUri: Uri? =
            intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                ?: intent
                    ?.clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.uri
                ?: intent?.data

        val mimeType = intent?.type.orEmpty()
        val isOpml =
            mimeType.contains("opml", ignoreCase = true) ||
                mimeType.contains("xml", ignoreCase = true) ||
                streamUri?.path?.endsWith(".opml", ignoreCase = true) == true ||
                streamUri?.toString()?.endsWith(".opml", ignoreCase = true) == true

        val initialOpmlUri = if (isOpml && streamUri != null) streamUri.toString() else null

        val initialText =
            when {
                initialOpmlUri != null -> {
                    null
                }

                intent?.action == Intent.ACTION_SEND -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                }

                else -> {
                    val raw = intent?.dataString.orEmpty()
                    when {
                        raw.startsWith("feed://", ignoreCase = true) -> "https://${raw.removePrefix("feed://")}"
                        raw.startsWith("feed:https://", ignoreCase = true) -> raw.removePrefix("feed:")
                        raw.startsWith("feed:http://", ignoreCase = true) -> raw.removePrefix("feed:")
                        raw.startsWith("rss://", ignoreCase = true) -> "https://${raw.removePrefix("rss://")}"
                        raw.startsWith("rss:https://", ignoreCase = true) -> raw.removePrefix("rss:")
                        raw.startsWith("rss:http://", ignoreCase = true) -> raw.removePrefix("rss:")
                        else -> raw
                    }
                }
            }

        setContent {
            CruxApp {
                var currentOpmlUri by remember { mutableStateOf(initialOpmlUri) }

                if (currentOpmlUri != null) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            finish()
                        },
                    ) {
                        OPMLImportSheet(
                            uri = currentOpmlUri!!.toUri(),
                            onBack = {
                                finish()
                            },
                        )
                    }
                } else {
                    ModalBottomSheet(
                        onDismissRequest = {
                            if (currentOpmlUri == null) {
                                finish()
                            }
                        },
                    ) {
                        RssSourceEditSheet(
                            onDismissRequest = {
                                if (currentOpmlUri == null) {
                                    finish()
                                }
                            },
                            id = null,
                            initialUrl = initialText,
                            onImportOPML = { opmlUri ->
                                currentOpmlUri = opmlUri
                            },
                        )
                    }
                }
            }
        }
    }
}
