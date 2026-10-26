package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.route.DeeplinkRoute

public fun ActionMenu.Companion.instagramLike(
    statusKey: MicroBlogKey,
    liked: Boolean,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "ig_like_$statusKey",
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
                PostEvent.Instagram.Like(
                    postKey = statusKey,
                    liked = liked,
                    count = count,
                    accountKey = accountKey,
                ),
            ),
        actionFamily = PostActionFamily.Like,
    )

public fun ActionMenu.Companion.instagramComment(
    statusKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "ig_comment_$statusKey",
        icon = UiIcon.Comment,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
        count = if (count > 0) UiNumber(count) else null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Compose.Reply(
                    accountKey = accountKey,
                    statusKey = statusKey,
                ),
            ),
        actionFamily = PostActionFamily.Reply,
    )

public fun ActionMenu.Companion.instagramShare(
    statusKey: MicroBlogKey,
    shareUrl: String,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "ig_share_$statusKey",
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
