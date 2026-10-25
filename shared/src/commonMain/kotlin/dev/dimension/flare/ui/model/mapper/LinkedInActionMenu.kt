package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiNumber

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
        clickEvent = ClickEvent.Noop,
        actionFamily = PostActionFamily.Favorite,
    )

public fun ActionMenu.Companion.linkedInComment(
    statusKey: MicroBlogKey,
    count: Long,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = "li_comment_$statusKey",
        icon = UiIcon.Comment,
        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
        count = if (count > 0) UiNumber(count) else null,
        clickEvent = ClickEvent.Noop,
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
        clickEvent = ClickEvent.Noop,
        actionFamily = PostActionFamily.Share,
    )
