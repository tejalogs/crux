package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Discord
import compose.icons.fontawesomeicons.brands.Github
import compose.icons.fontawesomeicons.brands.Telegram
import compose.icons.fontawesomeicons.solid.Language
import compose.icons.fontawesomeicons.solid.Lock
import dev.dimension.flare.BuildConfig
import dev.dimension.flare.Res
import dev.dimension.flare.app_name
import dev.dimension.flare.model.ilink
import dev.dimension.flare.settings_about_discord
import dev.dimension.flare.settings_about_discord_description
import dev.dimension.flare.settings_about_localization
import dev.dimension.flare.settings_about_localization_description
import dev.dimension.flare.settings_about_source_code
import dev.dimension.flare.settings_about_telegram
import dev.dimension.flare.settings_about_telegram_description
import dev.dimension.flare.settings_about_title
import dev.dimension.flare.settings_privacy_policy
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Header
import io.github.composefluent.ExperimentalFluentApi
import io.github.composefluent.component.Expander
import io.github.composefluent.component.ExpanderItem
import io.github.composefluent.component.ExpanderItemSeparator
import io.github.composefluent.component.HyperlinkButton
import io.github.composefluent.component.Text
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFluentApi::class)
@Composable
internal fun SettingsAboutSection(
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
) {
    Header(stringResource(Res.string.settings_about_title))
    Expander(
        icon = null,
        heading = {
            Text(stringResource(Res.string.app_name))
        },
        caption = {
            Text("${BuildConfig.versionName} (${BuildConfig.versionCode})")
        },
        expanded = expanded,
        onExpandedChanged = onExpandedChanged,
    ) {
        ExpanderItem(
            heading = {
                Text(text = stringResource(resource = Res.string.settings_about_source_code))
            },
            trailing = {
                HyperlinkButton(
                    "https://github.com/DimensionDev/Flare",
                ) {
                    Text(
                        text = "https://github.com/DimensionDev/Flare",
                        maxLines = 1,
                    )
                }
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Brands.Github,
                    contentDescription = "GitHub",
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(text = stringResource(resource = Res.string.settings_about_telegram))
            },
            caption = {
                Text(text = stringResource(resource = Res.string.settings_about_telegram_description))
            },
            trailing = {
                HyperlinkButton(
                    ilink,
                ) {
                    Text(
                        text = ilink,
                        maxLines = 1,
                    )
                }
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Brands.Telegram,
                    contentDescription = stringResource(resource = Res.string.settings_about_telegram),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(text = stringResource(resource = Res.string.settings_about_discord))
            },
            caption = {
                Text(text = stringResource(resource = Res.string.settings_about_discord_description))
            },
            trailing = {
                HyperlinkButton(
                    "https://discord.gg/De9NhXBryT",
                ) {
                    Text(
                        text = "https://discord.gg/De9NhXBryT",
                        maxLines = 1,
                    )
                }
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Brands.Discord,
                    contentDescription = stringResource(resource = Res.string.settings_about_discord),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(text = stringResource(resource = Res.string.settings_about_localization))
            },
            caption = {
                Text(text = stringResource(resource = Res.string.settings_about_localization_description))
            },
            trailing = {
                HyperlinkButton(
                    "https://crowdin.com/project/flareapp",
                ) {
                    Text(
                        text = "https://crowdin.com/project/flareapp",
                        maxLines = 1,
                    )
                }
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Language,
                    contentDescription = stringResource(resource = Res.string.settings_about_localization),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        ExpanderItemSeparator()
        ExpanderItem(
            heading = {
                Text(text = stringResource(resource = Res.string.settings_privacy_policy))
            },
            trailing = {
                HyperlinkButton(
                    "https://legal.mask.io/maskbook",
                ) {
                    Text(
                        text = "https://legal.mask.io/maskbook",
                        maxLines = 1,
                    )
                }
            },
            icon = {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.Lock,
                    contentDescription = stringResource(resource = Res.string.settings_privacy_policy),
                    modifier = Modifier.size(24.dp),
                )
            },
        )
    }
}
