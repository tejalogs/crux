package org.crux.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import moe.tlaster.precompose.molecule.producePresenter
import org.crux.compose.ui.Res
import org.crux.compose.ui.all_rss_feeds_title
import org.crux.compose.ui.antenna_title
import org.crux.compose.ui.cancel_button
import org.crux.compose.ui.channel_title
import org.crux.compose.ui.dm_list_title
import org.crux.compose.ui.fanbox_recommended_creators_title
import org.crux.compose.ui.fanbox_supported_title
import org.crux.compose.ui.home_tab_bookmarks_title
import org.crux.compose.ui.home_tab_discover_title
import org.crux.compose.ui.home_tab_favorite_title
import org.crux.compose.ui.home_tab_featured_title
import org.crux.compose.ui.home_tab_feeds_title
import org.crux.compose.ui.home_tab_following_title
import org.crux.compose.ui.home_tab_for_you_title
import org.crux.compose.ui.home_tab_home_title
import org.crux.compose.ui.home_tab_list_title
import org.crux.compose.ui.home_tab_me_title
import org.crux.compose.ui.home_tab_notifications_title
import org.crux.compose.ui.home_tab_popular_title
import org.crux.compose.ui.illustrations_title
import org.crux.compose.ui.liked_title
import org.crux.compose.ui.login_button
import org.crux.compose.ui.manga_title
import org.crux.compose.ui.media_title
import org.crux.compose.ui.mixed_timeline_title
import org.crux.compose.ui.pixiv_private_favourites_title
import org.crux.compose.ui.pixiv_private_following_title
import org.crux.compose.ui.pixiv_ranking_day_female_title
import org.crux.compose.ui.pixiv_ranking_day_male_title
import org.crux.compose.ui.pixiv_ranking_day_manga_title
import org.crux.compose.ui.pixiv_ranking_month_title
import org.crux.compose.ui.pixiv_ranking_week_original_title
import org.crux.compose.ui.pixiv_ranking_week_rookie_title
import org.crux.compose.ui.pixiv_ranking_week_title
import org.crux.compose.ui.posts_title
import org.crux.compose.ui.posts_with_replies_title
import org.crux.compose.ui.profile_tab_articles
import org.crux.compose.ui.profile_tab_highlights
import org.crux.compose.ui.rss_title
import org.crux.compose.ui.service_select_next_button
import org.crux.compose.ui.settings_title
import org.crux.compose.ui.social_title
import org.crux.compose.ui.tab_settings_default
import org.crux.compose.ui.tab_settings_filter_repost
import org.crux.data.model.IconType
import org.crux.data.model.tab.UiTimelineTabItem
import org.crux.model.AccountType
import org.crux.ui.component.platform.PlatformText
import org.crux.ui.component.platform.PlatformTextStyle
import org.crux.ui.model.UiStrings
import org.crux.ui.model.UiText
import org.crux.ui.model.onLoading
import org.crux.ui.model.onSuccess
import org.crux.ui.presenter.home.FavIconPresenter
import org.crux.ui.presenter.home.UserPresenter
import org.crux.ui.presenter.invoke
import org.crux.ui.theme.PlatformContentColor
import org.crux.ui.theme.PlatformTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val FavIconShape = RoundedCornerShape(4.dp)

@Composable
public fun Text(
    text: UiText,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = PlatformTextStyle.current,
) {
    PlatformText(
        text =
            when (text) {
                is UiText.Localized -> stringResource(text.string.res)
                is UiText.Raw -> text.string
            },
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        style = style,
        maxLines = 1,
    )
}

@Composable
public fun TabIcon(
    tabItem: UiTimelineTabItem,
    icon: IconType = tabItem.icon,
    title: UiText = tabItem.title,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    iconOnly: Boolean = false,
    color: Color = PlatformContentColor.current,
) {
    TabIcon(
        icon = icon,
        title = title,
        modifier = modifier,
        size = size,
        iconOnly = iconOnly,
        color = color,
    )
}

@Composable
public fun TabIcon(
    icon: IconType,
    title: UiText,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    color: Color = PlatformContentColor.current,
    size: Dp = 24.dp,
) {
    val text =
        when (title) {
            is UiText.Localized -> stringResource(title.string.res)
            is UiText.Raw -> title.string
        }
    when (icon) {
        is IconType.Avatar -> {
            val userState by producePresenter(key = "avatar:${icon.accountKey}") {
                remember(icon.accountKey) {
                    UserPresenter(
                        AccountType.Specific(icon.accountKey),
                        icon.accountKey,
                    )
                }.invoke()
            }
            userState.user
                .onSuccess {
                    AvatarComponent(it.avatar, size = size, modifier = modifier)
                }.onLoading {
                    AvatarComponent(null, size = size, modifier = modifier.placeholder(true))
                }
        }

        is IconType.Material -> {
            FAIcon(
                imageVector = icon.icon.toImageVector(),
                contentDescription = text,
                modifier =
                    modifier
                        .size(size),
                tint = color,
            )
        }

        is IconType.Mixed -> {
            if (iconOnly) {
                FAIcon(
                    imageVector = icon.icon.toImageVector(),
                    contentDescription = text,
                    modifier =
                        modifier
                            .size(size),
                    tint = color,
                )
            } else {
                val userState by producePresenter(key = "avatar:${icon.accountKey}") {
                    remember(icon.accountKey) {
                        UserPresenter(
                            AccountType.Specific(icon.accountKey),
                            icon.accountKey,
                        )
                    }.invoke()
                }
                Box(
                    modifier = modifier,
                ) {
                    userState.user
                        .onSuccess {
                            AvatarComponent(it.avatar, size = size)
                        }.onLoading {
                            AvatarComponent(
                                null,
                                size = size,
                                modifier = Modifier.placeholder(true),
                            )
                        }
                    FAIcon(
                        imageVector = icon.icon.toImageVector(),
                        contentDescription = text,
                        modifier =
                            Modifier
                                .size(size / 2)
                                .align(Alignment.BottomEnd)
                                .background(PlatformTheme.colorScheme.card, shape = CircleShape)
                                .padding(2.dp),
                    )
                }
            }
        }

        is IconType.Url -> {
            NetworkImage(
                icon.url,
                contentDescription = text,
                modifier =
                    modifier
                        .size(size)
                        .clip(FavIconShape),
                contentScale = ContentScale.Fit,
            )
        }

        is IconType.FavIcon -> {
            FavIcon(icon.host, modifier, title, size)
        }
    }
}

@Composable
public fun FavIcon(
    host: String,
    modifier: Modifier = Modifier,
    title: UiText? = null,
    size: Dp = 24.dp,
) {
    val iconState by producePresenter(key = "fav:$host") {
        remember(host) {
            FavIconPresenter(
                host,
            )
        }.invoke()
    }
    iconState
        .onSuccess {
            NetworkImage(
                it,
                contentDescription =
                    when (title) {
                        is UiText.Localized -> stringResource(title.string.res)
                        is UiText.Raw -> title.string
                        null -> null
                    },
                modifier =
                    modifier
                        .size(size)
                        .clip(FavIconShape),
                contentScale = ContentScale.Fit,
            )
        }.onLoading {
            Box(
                modifier =
                    modifier
                        .size(size)
                        .clip(FavIconShape)
                        .placeholder(true),
            ) {
            }
        }
}

internal val UiStrings.res: StringResource
    get() =
        when (this) {
            UiStrings.Default -> Res.string.tab_settings_default

            UiStrings.Home -> Res.string.home_tab_home_title

            UiStrings.Notifications -> Res.string.home_tab_notifications_title

            UiStrings.Discover -> Res.string.home_tab_discover_title

            UiStrings.Me -> Res.string.home_tab_me_title

            UiStrings.Settings -> Res.string.settings_title

            UiStrings.Featured -> Res.string.home_tab_featured_title

            UiStrings.ForYou -> Res.string.home_tab_for_you_title

            UiStrings.Popular -> Res.string.home_tab_popular_title

            UiStrings.Bookmark -> Res.string.home_tab_bookmarks_title

            UiStrings.Favourite -> Res.string.home_tab_favorite_title

            UiStrings.Following -> Res.string.home_tab_following_title

            UiStrings.List -> Res.string.home_tab_list_title

            UiStrings.Feeds -> Res.string.home_tab_feeds_title

            UiStrings.DirectMessage -> Res.string.dm_list_title

            UiStrings.Rss -> Res.string.rss_title

            UiStrings.Social -> Res.string.social_title

            UiStrings.Antenna -> Res.string.antenna_title

            UiStrings.MixedTimeline -> Res.string.mixed_timeline_title

            UiStrings.Liked -> Res.string.liked_title

            UiStrings.AllRssFeeds -> Res.string.all_rss_feeds_title

            UiStrings.Posts -> Res.string.posts_title

            UiStrings.PostsWithReplies -> Res.string.posts_with_replies_title

            UiStrings.Highlights -> Res.string.profile_tab_highlights

            UiStrings.Reposts -> Res.string.tab_settings_filter_repost

            UiStrings.Articles -> Res.string.profile_tab_articles

            UiStrings.Media -> Res.string.media_title

            UiStrings.Channel -> Res.string.channel_title

            UiStrings.Login -> Res.string.login_button

            UiStrings.Verify -> Res.string.login_button

            UiStrings.Cancel -> Res.string.cancel_button

            UiStrings.Next -> Res.string.service_select_next_button

            // Values kept only so saved tabs still deserialize; nothing shows them any more.
            UiStrings.MastodonLocal,
            UiStrings.MastodonPublic,
            -> Res.string.tab_settings_default

            UiStrings.Username,
            UiStrings.Password,
            UiStrings.Otp,
            UiStrings.OAuthLogin,
            UiStrings.PasswordLogin,
            UiStrings.QrConnect,
            UiStrings.CredentialImport,
            UiStrings.ExternalSigner,
            UiStrings.NostrLoginAccount,
            UiStrings.BlueskyFixDelegationScopes,
            UiStrings.WebCookieLogin,
            -> Res.string.login_button

            UiStrings.PixivRankingWeek -> Res.string.pixiv_ranking_week_title

            UiStrings.PixivRankingMonth -> Res.string.pixiv_ranking_month_title

            UiStrings.PixivRankingDayMale -> Res.string.pixiv_ranking_day_male_title

            UiStrings.PixivRankingDayFemale -> Res.string.pixiv_ranking_day_female_title

            UiStrings.PixivRankingWeekOriginal -> Res.string.pixiv_ranking_week_original_title

            UiStrings.PixivRankingWeekRookie -> Res.string.pixiv_ranking_week_rookie_title

            UiStrings.PixivRankingDayManga -> Res.string.pixiv_ranking_day_manga_title

            UiStrings.Illustrations -> Res.string.illustrations_title

            UiStrings.Manga -> Res.string.manga_title

            UiStrings.FanboxSupported -> Res.string.fanbox_supported_title

            UiStrings.FanboxRecommendedCreators -> Res.string.fanbox_recommended_creators_title

            UiStrings.PixivPrivateFollowing -> Res.string.pixiv_private_following_title

            UiStrings.PixivPrivateBookmarks,
            UiStrings.PixivPrivateFavourites,
            -> Res.string.pixiv_private_favourites_title
        }
