package org.crux.ui.model.mapper

import org.crux.data.datasource.microblog.ActionMenu
import org.crux.data.datasource.microblog.PostActionFamily
import org.crux.data.datasource.microblog.PostEvent
import org.crux.model.AccountType
import org.crux.model.MicroBlogKey
import org.crux.ui.model.ClickEvent
import org.crux.ui.model.UiIcon
import org.crux.ui.model.UiNumber
import org.crux.ui.route.DeeplinkRoute

public fun ActionMenu.Companion.linkedInLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "li_like_$statusKey",
        icon = if (liked) UiIcon.Unlike else UiIcon.Like,
        text =
            ActionMenu.Item.Text.Localized(
                if (liked) ActionMenu.Item.Text.Localized.Type.Unlike else ActionMenu.Item.Text.Localized.Type.Like,
            ),
        count = if (count > 0) UiNumber(count) else null,
        color = if (liked) ActionMenu.Item.Color.Red else null,
        clickEvent =
            ClickEvent.event(
                accountKey,
                PostEvent.LinkedIn.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Like,
    )

public fun ActionMenu.Companion.linkedInComment(
    statusKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey,
    initialText: String = "",
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "li_comment_$statusKey",
        icon = UiIcon.Comment,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
        count = if (count > 0) UiNumber(count) else null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Compose.Reply(
                    accountKey = accountKey,
                    statusKey = statusKey,
                    initialText = initialText,
                ),
            ),
        actionFamily = PostActionFamily.Reply,
    )

public fun ActionMenu.Companion.linkedInShare(
    statusKey: MicroBlogKey,
    shareUrl: String,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "li_share_$statusKey",
        icon = UiIcon.Share,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.ShareSheet(
                    statusKey = statusKey,
                    accountType = AccountType.Specific(accountKey),
                    shareUrl = shareUrl,
                ),
            ),
        actionFamily = PostActionFamily.Share,
    )
