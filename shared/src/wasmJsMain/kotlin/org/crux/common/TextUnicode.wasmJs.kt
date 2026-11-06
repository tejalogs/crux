@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package org.crux.common

private fun normalize(text: String): String = js("text.normalize('NFC')")

public actual fun String.normalizeNfc(): String = normalize(this)
