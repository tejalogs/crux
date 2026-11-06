package org.crux.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleExclamation
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import org.crux.compose.ui.Res
import org.crux.compose.ui.login_expired
import org.crux.compose.ui.login_expired_message
import org.crux.compose.ui.permission_denied_message
import org.crux.compose.ui.permission_denied_title
import org.crux.compose.ui.status_loadmore_error
import org.crux.data.repository.LoginExpiredException
import org.crux.data.repository.RequireReLoginException
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.route.DeeplinkRoute
import org.crux.ui.route.toUri
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (error) {
        is LoginExpiredException -> {
            LoginExpiredError(error, modifier)
        }

        is RequireReLoginException -> {
            RequireReLoginError(error, modifier)
        }

        else -> {
            CommonError(
                error = error,
                onRetry = onRetry,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CommonError(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable {
                    onRetry.invoke()
                }.fillMaxWidth()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.FileCircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(text = stringResource(Res.string.status_loadmore_error))
        error.message?.let { PlatformText(text = it) }
    }
}

@Composable
private fun RequireReLoginError(
    error: RequireReLoginException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(
                        DeeplinkRoute
                            .Relogin(
                                accountKey = error.accountKey,
                                platformId = error.platformId,
                            ).toUri(),
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.permission_denied_title),
        )
        PlatformText(
            text = stringResource(resource = Res.string.permission_denied_message),
        )
    }
}

@Composable
private fun LoginExpiredError(
    error: LoginExpiredException,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier =
            modifier
                .clickable {
                    uriHandler.openUri(
                        DeeplinkRoute
                            .Relogin(
                                accountKey = error.accountKey,
                                platformId = error.platformId,
                            ).toUri(),
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        FAIcon(
            imageVector = FontAwesomeIcons.Solid.CircleExclamation,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired),
        )
        PlatformText(
            text = stringResource(resource = Res.string.login_expired_message),
        )
        PlatformText(
            text = error.accountKey.toString(),
        )
    }
}
