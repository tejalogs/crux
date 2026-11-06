package org.crux.ui.common

import org.crux.ui.route.APPSCHEMA

internal fun String.isLoginCallbackDeepLink(): Boolean =
    startsWith(
        prefix = "$APPSCHEMA://Callback/SignIn/",
        ignoreCase = true,
    )
