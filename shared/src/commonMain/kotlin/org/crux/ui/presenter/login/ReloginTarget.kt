package org.crux.ui.presenter.login

import org.crux.model.MicroBlogKey

public data class ReloginTarget(
    val accountKey: MicroBlogKey,
    val platformId: String,
)
