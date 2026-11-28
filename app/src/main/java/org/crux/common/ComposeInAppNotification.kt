package org.crux.common

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.crux.R
import org.crux.ui.presenter.login.ReloginTarget
import org.koin.core.annotation.Single

internal class Event<out T>(
    private val content: T?,
    initialHandled: Boolean = false,
) {
    private var hasBeenHandled = initialHandled

    fun getContentIfNotHandled(): T? =
        if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
}

internal sealed interface Notification {
    data class Progress(
        val progress: Int,
        val total: Int,
    ) : Notification {
        val percentage: Float
            get() = progress.toFloat() / total
    }

    data class StringNotification(
        @param:StringRes
        @field:StringRes
        val messageId: Int,
        val success: Boolean,
        val args: List<Any> = emptyList(),
        val reloginTarget: ReloginTarget? = null,
    ) : Notification
}

@Single(binds = [InAppNotification::class])
internal class ComposeInAppNotification : InAppNotification {
    private val _source = MutableStateFlow(Event<Notification>(null, initialHandled = true))
    val source
        get() = _source.asStateFlow()

    override fun onProgress(
        message: Message,
        progress: Int,
        total: Int,
    ) {
        _source.value = Event(Notification.Progress(progress, total))
    }

    override fun onSuccess(message: Message) {
        _source.value = Event(Notification.StringNotification(message.successTitle, success = true))
    }

    fun message(
        @StringRes messageId: Int,
    ) {
        _source.value = Event(Notification.StringNotification(messageId, success = true))
    }

    fun errorMessage(
        @StringRes messageId: Int,
    ) {
        _source.value = Event(Notification.StringNotification(messageId, success = false))
    }

    override fun onError(
        message: Message,
        throwable: Throwable,
    ) {
        _source.value =
            Event(
                Notification.StringNotification(
                    message.errorTitle,
                    success = false,
                ),
            )
    }
}

private val Message.successTitle
    get() =
        when (this) {
            Message.Compose -> R.string.compose_notification_success
            Message.LoginExpired -> R.string.notification_login_expired
        }

private val Message.errorTitle
    get() =
        when (this) {
            Message.Compose -> R.string.compose_notification_error
            Message.LoginExpired -> R.string.notification_login_expired
        }
