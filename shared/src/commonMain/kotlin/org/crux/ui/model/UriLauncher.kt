package org.crux.ui.model

import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri

public fun interface UriLauncher {
    public fun launch(uri: String)
}

public fun UriLauncher.launch(route: DeeplinkRoute) {
    launch(route.toUri())
}
