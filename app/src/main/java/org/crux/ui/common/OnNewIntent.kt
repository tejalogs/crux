package org.crux.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer

private fun Context.getActivity(): Activity {
    if (this is Activity) return this
    return if (this is ContextWrapper) baseContext.getActivity() else getActivity()
}

@Composable
fun OnNewIntent(
    withOnCreateIntent: Boolean = false,
    onNewIntent: (Intent) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = (context.getActivity() as ComponentActivity)
        // Each intent is handled once. Without the mark, re-entering composition would replay the
        // activity's last intent (for example reopening a compose sheet another app prefilled).
        val handleOnce: (Intent) -> Unit = { intent ->
            if (!intent.getBooleanExtra(EXTRA_INTENT_HANDLED, false)) {
                intent.putExtra(EXTRA_INTENT_HANDLED, true)
                onNewIntent(intent)
            }
        }
        val listener = Consumer<Intent> { handleOnce(it) }
        activity.addOnNewIntentListener(listener)
        if (withOnCreateIntent) {
            activity.intent?.let(handleOnce)
        }
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

private const val EXTRA_INTENT_HANDLED = "org.crux.extra.INTENT_HANDLED"
