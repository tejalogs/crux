package org.crux.ui.screen.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import org.crux.MainActivity
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri

/**
 * Entry point for the share sheet, the quick-settings tile and the app shortcut. It draws nothing:
 * it hands the shared text to Crux's write-once compose sheet inside [MainActivity] and finishes.
 * (The classic composer needed an API account, and Crux has none.)
 */
class ShortcutComposeActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INITIAL_TEXT = "org.crux.extra.INITIAL_TEXT"
        const val EXTRA_INITIAL_CURSOR_POSITION = "org.crux.extra.INITIAL_CURSOR_POSITION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text =
            when {
                intent?.hasExtra(EXTRA_INITIAL_TEXT) == true -> {
                    intent.getStringExtra(EXTRA_INITIAL_TEXT).orEmpty()
                }

                intent?.action == Intent.ACTION_SEND -> {
                    sharedTextWithTitle(
                        text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                        title =
                            intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString(),
                    )
                }

                else -> {
                    ""
                }
            }
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                DeeplinkRoute.Compose
                    .Crux(initialText = text)
                    .toUri()
                    .toUri(),
            ).setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
