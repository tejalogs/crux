package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collection.MutableVector
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CaretDown
import compose.icons.fontawesomeicons.solid.CaretUp
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.PenToSquare
import dev.dimension.flare.R
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.ui.theme.segmentedShapes2
import soup.compose.material.motion.animation.materialElevationScaleIn
import soup.compose.material.motion.animation.materialElevationScaleOut

val LocalBottomBarHeight = androidx.compose.runtime.staticCompositionLocalOf<Dp> { 0.dp }
val LocalBottomBarShowing = androidx.compose.runtime.staticCompositionLocalOf<Boolean> { false }

/**
 * Lets the screen behind a bottom-bar item replace that item with its own expanded content while
 * it is selected (e.g. Home grows into the home feed tabs). The content receives the item's
 * onClick so it can keep "tap again to scroll to top".
 */
class BottomBarAccessory {
    var content: (@Composable (onReselect: () -> Unit) -> Unit)? by mutableStateOf(null)
    var onLongClick: (() -> Unit)? by mutableStateOf(null)

    // Unread count for destinations that live in the side panel on phones (e.g. Notifications),
    // so the drawer button inside the expanded content can show a dot.
    var badgeCount: Int by mutableStateOf(0)

    // Live web tabs send no scroll events to Compose, so a hidden dock could never come back there.
    var keepVisible: Boolean by mutableStateOf(false)
}

val LocalBottomBarAccessory = androidx.compose.runtime.staticCompositionLocalOf<BottomBarAccessory?> { null }

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
)
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
@Composable
fun NavigationSuiteScaffold2(
    navigationSuiteItems: NavigationSuiteScope2.() -> Unit,
    secondaryItems: NavigationSuiteScope2.() -> Unit,
    wideNavigationRailState: WideNavigationRailState,
    modifier: Modifier = Modifier,
    bottomBarAutoHideEnabled: Boolean = true,
    showFab: Boolean = true,
    keepBottomBarVisible: Boolean = false,
    // Overrides when the phone bottom bar shows; by default it shows while one of its items is selected.
    bottomBarVisible: Boolean? = null,
    onFabClicked: () -> Unit = {},
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2()),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    railHeader: @Composable (ColumnScope.() -> Unit)? = null,
    footerItems: NavigationSuiteScope2.() -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    var isPodcastShowing by remember { mutableStateOf(false) }
    var isBottomBarExpanded by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(keepBottomBarVisible) {
        if (keepBottomBarVisible) isBottomBarExpanded = true
    }
    val bottomBarStyle = LocalGlobalAppearance.current.bottomBarStyle
    val bottomBarBehavior = LocalGlobalAppearance.current.bottomBarBehavior
    val showBottomBarLabels = LocalGlobalAppearance.current.showBottomBarLabels
    val bottomBarHeight =
        remember(showBottomBarLabels) {
            if (showBottomBarLabels) {
                56.dp
            } else {
                48.dp
            }
        }
    val bottomBarState =
        remember(
            bottomBarStyle,
            bottomBarBehavior,
            isBottomBarExpanded,
            bottomBarAutoHideEnabled,
        ) {
            when (bottomBarStyle) {
                BottomBarStyle.Floating -> {
                    when (bottomBarBehavior) {
                        BottomBarBehavior.AlwaysShow -> {
                            BottomBarState.FloatingNormal
                        }

                        BottomBarBehavior.HideOnScroll -> {
                            if (!isBottomBarExpanded && bottomBarAutoHideEnabled) {
                                BottomBarState.FloatingHidden
                            } else {
                                BottomBarState.FloatingNormal
                            }
                        }

                        BottomBarBehavior.MinimizeOnScroll -> {
                            if (!isBottomBarExpanded && bottomBarAutoHideEnabled) {
                                BottomBarState.FloatingMinimized
                            } else {
                                BottomBarState.FloatingNormal
                            }
                        }
                    }
                }

                BottomBarStyle.Classic -> {
                    when (bottomBarBehavior) {
                        BottomBarBehavior.AlwaysShow -> {
                            BottomBarState.ClassicNormal
                        }

                        BottomBarBehavior.HideOnScroll -> {
                            if (!isBottomBarExpanded && bottomBarAutoHideEnabled) {
                                BottomBarState.ClassicHidden
                            } else {
                                BottomBarState.ClassicNormal
                            }
                        }

                        BottomBarBehavior.MinimizeOnScroll -> {
                            if (!isBottomBarExpanded && bottomBarAutoHideEnabled) {
                                BottomBarState.ClassicHidden
                            } else {
                                BottomBarState.ClassicNormal
                            }
                        }
                    }
                }
            }
        }
    Surface(
        modifier =
            modifier
                .let {
                    if (bottomBarAutoHideEnabled && !keepBottomBarVisible) {
                        it.floatingToolbarVerticalNestedScroll(
                            expanded = isBottomBarExpanded,
                            onExpand = { isBottomBarExpanded = true },
                            onCollapse = { isBottomBarExpanded = false },
                        )
                    } else {
                        it
                    }
                },
        color = containerColor,
        contentColor = contentColor,
    ) {
        val scope by rememberStateOfItems(navigationSuiteItems)
        val footerScope by rememberStateOfItems(footerItems)
        val secondaryScope by rememberStateOfItems(secondaryItems)
        val showBottomBar =
            layoutType == NavigationSuiteType.NavigationBar &&
                (
                    bottomBarVisible
                        ?: scope.itemList.any { item -> item is NavigationSuiteItem.DefaultItem && item.selected }
                )

        Row {
            ModalWideNavigationRail(
                hideOnCollapse = layoutType == NavigationSuiteType.NavigationBar,
                state = wideNavigationRailState,
                colors =
                    navigationSuiteColors.wideNavigationRailColors
                        .copy(
                            containerColor = Color.Transparent,
                            modalContainerColor = MaterialTheme.colorScheme.background,
                            modalContentColor = contentColorFor(MaterialTheme.colorScheme.background),
                        ),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                val actualLayoutType =
                    if (layoutType == NavigationSuiteType.NavigationBar) {
                        NavigationSuiteType.NavigationDrawer
                    } else if (layoutType == NavigationSuiteType.NavigationRail) {
                        if (wideNavigationRailState.currentValue == WideNavigationRailValue.Expanded) {
                            NavigationSuiteType.WideNavigationRailExpanded
                        } else {
                            NavigationSuiteType.WideNavigationRailCollapsed
                        }
                    } else {
                        layoutType
                    }
                Column(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            if (layoutType == NavigationSuiteType.NavigationBar) {
                                0.dp
                            } else {
                                8.dp
                            },
                        ),
                ) {
                    railHeader?.invoke(this)
                    if (layoutType != NavigationSuiteType.NavigationBar) {
                        NavigationSuiteItemGroup(
                            provider = scope,
                            layoutType = layoutType,
                            actualLayoutType = actualLayoutType,
                        )
                    }
                    if (wideNavigationRailState.currentValue == WideNavigationRailValue.Expanded) {
                        NavigationSuiteItemGroup(
                            provider = secondaryScope,
                            layoutType = layoutType,
                            actualLayoutType = actualLayoutType,
                        )
                        if (layoutType == NavigationSuiteType.NavigationBar) {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                    if (layoutType != NavigationSuiteType.NavigationBar) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    NavigationSuiteItemGroup(
                        provider = footerScope,
                        layoutType = layoutType,
                        actualLayoutType = actualLayoutType,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
            Box {
                CompositionLocalProvider(
                    LocalBottomBarHeight provides
                        if (isPodcastShowing) {
                            bottomBarHeight
                        } else {
                            0.dp
                        } +
                        if (showBottomBar) {
                            when (bottomBarStyle) {
                                BottomBarStyle.Floating -> bottomBarHeight + 16.dp
                                BottomBarStyle.Classic -> bottomBarHeight + 8.dp
                            }
                        } else {
                            0.dp
                        } +
                        with(LocalDensity.current) {
                            WindowInsets.systemBars
                                .only(
                                    WindowInsetsSides.Bottom,
                                ).getBottom(this)
                                .toDp()
                        },
                    LocalBottomBarShowing provides (layoutType == NavigationSuiteType.NavigationBar),
                ) {
                    content.invoke()
                }

                val isFloating =
                    remember(bottomBarState) {
                        when (bottomBarState) {
                            BottomBarState.FloatingNormal -> true
                            BottomBarState.FloatingMinimized -> true
                            BottomBarState.FloatingHidden -> true
                            BottomBarState.ClassicNormal -> false
                            BottomBarState.ClassicHidden -> false
                        }
                    }
                val isHidden =
                    remember(bottomBarState) {
                        when (bottomBarState) {
                            BottomBarState.FloatingNormal -> false
                            BottomBarState.FloatingMinimized -> false
                            BottomBarState.FloatingHidden -> true
                            BottomBarState.ClassicNormal -> false
                            BottomBarState.ClassicHidden -> true
                        }
                    }
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            ).let {
                                if (!isHidden) {
                                    if (isFloating) {
                                        it.padding(bottom = bottomBarHeight)
                                    } else {
                                        it.padding(bottom = bottomBarHeight + 24.dp, end = bottomBarHeight)
                                    }
                                } else {
                                    it
                                }
                            }.animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    PodcastFAB(
                        onVisibilityChanged = {
                            isPodcastShowing = it
                        },
                        modifier =
                            Modifier
                                .let {
                                    if (layoutType == NavigationSuiteType.NavigationBar) {
                                        it
                                    } else {
                                        it.windowInsetsPadding(
                                            WindowInsets.systemBars.only(
                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                            ),
                                        )
                                    }
                                },
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    showBottomBar &&
                        (
                            bottomBarState != BottomBarState.FloatingHidden &&
                                bottomBarState != BottomBarState.ClassicHidden
                        ),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                    ) {
                        SharedTransitionLayout(
                            modifier = Modifier.align(Alignment.BottomStart),
                        ) {
                            AnimatedContent(
                                bottomBarState,
                            ) { bottomBarState ->
                                BottomBar(
                                    state = bottomBarState,
                                    provider = scope,
                                    showFab = showFab,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    showBottomBarLabels = showBottomBarLabels,
                                )
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            showFab,
                            modifier =
                                Modifier.align(Alignment.BottomEnd),
                            enter =
                                materialElevationScaleIn(
                                    initialAlpha = 0f,
                                ),
                            exit =
                                materialElevationScaleOut(
                                    targetAlpha = 0f,
                                ),
                        ) {
                            SharedTransitionLayout {
                                AnimatedContent(
                                    bottomBarState,
                                ) { bottomBarState ->
                                    BottomFab(
                                        state = bottomBarState,
                                        bottomBarHeight = bottomBarHeight,
                                        animatedVisibilityScope = this@AnimatedContent,
                                        onFabClicked = onFabClicked,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Floating dock and compose FAB share one height so their edges line up.
private val DockHeight = 56.dp

// Compose FAB (56dp) plus its 16dp end padding and a 16dp gap, minus the bar's own 16dp end padding.
private val FabReservedWidth = 72.dp

enum class BottomBarState {
    FloatingNormal,
    FloatingMinimized,
    FloatingHidden,
    ClassicNormal,
    ClassicHidden,
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharedTransitionScope.BottomFab(
    state: BottomBarState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onFabClicked: () -> Unit,
    bottomBarHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val isFloating =
        remember(state) {
            when (state) {
                BottomBarState.FloatingNormal -> true
                BottomBarState.FloatingMinimized -> true
                BottomBarState.FloatingHidden -> true
                BottomBarState.ClassicNormal -> false
                BottomBarState.ClassicHidden -> false
            }
        }
    val isExpanded =
        remember(state) {
            when (state) {
                BottomBarState.FloatingNormal -> true
                BottomBarState.FloatingMinimized -> true
                BottomBarState.FloatingHidden -> true
                BottomBarState.ClassicNormal -> true
                BottomBarState.ClassicHidden -> true
            }
        }
    // M3 Expressive: cookie-shaped FAB that spins a little while pressed.
    val fabInteractionSource = remember { MutableInteractionSource() }
    val isPressed by fabInteractionSource.collectIsPressedAsState()
    val pressRotation by animateFloatAsState(
        targetValue = if (isPressed) 30f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "fab_press_rotation",
    )
    val fabShape = MaterialShapes.Cookie9Sided.toShape()
    Surface(
        onClick = onFabClicked,
        shape = fabShape,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        interactionSource = fabInteractionSource,
        modifier =
            modifier
                .sharedElement(
                    rememberSharedContentState("compose_fab"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ).padding(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ).windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).let {
                    if (isFloating) {
                        it
                    } else {
                        it.padding(
                            bottom = bottomBarHeight + 24.dp,
                        )
                    }
                }.graphicsLayer { rotationZ = pressRotation }
                .size(
                    if (isExpanded) {
                        DockHeight
                    } else {
                        44.dp
                    },
                ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FontAwesomeIcons.Solid.PenToSquare,
                contentDescription = stringResource(id = R.string.compose_title),
                modifier =
                    Modifier
                        .size(22.dp)
                        .graphicsLayer { rotationZ = -pressRotation },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.BottomBar(
    state: BottomBarState,
    provider: NavigationSuiteItemProvider,
    animatedVisibilityScope: AnimatedVisibilityScope,
    showBottomBarLabels: Boolean,
    showFab: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerShape =
        remember(state) {
            when (state) {
                BottomBarState.FloatingNormal -> CircleShape
                BottomBarState.FloatingMinimized -> CircleShape
                BottomBarState.FloatingHidden -> CircleShape
                BottomBarState.ClassicNormal -> RoundedCornerShape(0)
                BottomBarState.ClassicHidden -> RoundedCornerShape(0)
            }
        }
    val isFloating =
        remember(state) {
            when (state) {
                BottomBarState.FloatingNormal -> true
                BottomBarState.FloatingMinimized -> true
                BottomBarState.FloatingHidden -> true
                BottomBarState.ClassicNormal -> false
                BottomBarState.ClassicHidden -> false
            }
        }
    val isExpanded =
        remember(state) {
            when (state) {
                BottomBarState.FloatingNormal -> true
                BottomBarState.FloatingMinimized -> false
                BottomBarState.FloatingHidden -> true
                BottomBarState.ClassicNormal -> true
                BottomBarState.ClassicHidden -> true
            }
        }
    // The selected item's screen may expand it in place (Home → feed tabs). The bar then stretches
    // to fill the row, stopping short of the floating compose button.
    val accessoryItem =
        provider.itemList
            .asMutableList()
            .firstOrNull { it is NavigationSuiteItem.DefaultItem && it.selected && it.expandedContent != null }
    val hasAccessory = accessoryItem != null

    Glassify(
        shape = containerShape,
        shadowElevation = if (isFloating) 10.dp else 4.dp,
        tonalElevation = 6.dp,
        border = if (isFloating) BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
        color =
            if (isFloating) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        modifier =
            modifier
                .sharedElement(
                    rememberSharedContentState("bottom_surface"),
                    animatedVisibilityScope = animatedVisibilityScope,
                ).let {
                    if (isFloating) {
                        it
                            .padding(
                                horizontal = 16.dp,
                                vertical = 8.dp,
                            ).windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            )
                    } else {
                        it
                    }
                }.let {
                    if (hasAccessory && isFloating) {
                        it
                            .fillMaxWidth()
                            .padding(end = if (showFab) FabReservedWidth else 0.dp)
                    } else {
                        it
                    }
                },
    ) {
        Row(
            modifier =
                Modifier
                    // Inside the surface: outside it, this clips the shadow to a visible rectangle.
                    .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())
                    .let {
                        if (hasAccessory) it.fillMaxWidth() else it
                    }.let {
                        if (isFloating) {
                            it
                        } else {
                            it.windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                ),
                            )
                        }
                    }.let {
                        // After the insets, so the system bar can't squash the classic dock.
                        if (hasAccessory) it.height(DockHeight) else it
                    }.padding(
                        horizontal = if (hasAccessory) 6.dp else 12.dp,
                        vertical = if (hasAccessory) 0.dp else 4.dp,
                    ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val defaultItems = provider.itemList.asMutableList().filterIsInstance<NavigationSuiteItem.DefaultItem>()
            defaultItems.forEachIndexed { index, navItem ->
                val expandedContent = navItem.expandedContent
                if (hasAccessory && navItem.selected && expandedContent != null) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .sharedElement(
                                    rememberSharedContentState("item_$index"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ),
                    ) {
                        expandedContent(navItem.onClick)
                    }
                } else if (isExpanded || navItem.selected) {
                    ShortNavigationBarItem(
                        modifier =
                            navItem.modifier
                                .sharedElement(
                                    rememberSharedContentState("item_$index"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                ).let {
                                    if (isFloating || hasAccessory) {
                                        it
                                    } else {
                                        it.weight(1f)
                                    }
                                },
                        selected = navItem.selected,
                        onClick = navItem.onClick,
                        icon = {
                            Box(
                                modifier =
                                    Modifier
                                        .sharedElement(
                                            rememberSharedContentState(
                                                "item_icon_$index",
                                            ),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        ),
                            ) {
                                NavigationItemIcon(
                                    badge = navItem.badge,
                                    icon = navItem.icon,
                                )
                            }
                        },
                        enabled = navItem.enabled,
                        label = if (isExpanded && showBottomBarLabels) navItem.label else null,
                        interactionSource = navItem.interactionSource,
                    )
                }
            }
        }
    }
}

private interface NavigationSuiteItemProvider {
    val itemsCount: Int
    val itemList: MutableVector<NavigationSuiteItem>
}

private data class SegmentedGroupPosition(
    val index: Int,
    val size: Int,
)

private fun NavigationSuiteItemProvider.segmentedGroupPosition(
    itemIndex: Int,
    isExpanded: (Int) -> Boolean,
): SegmentedGroupPosition {
    val item = itemList[itemIndex]
    if (item is NavigationSuiteItem.HeaderItem) {
        return SegmentedGroupPosition(index = 0, size = 1)
    }
    val isExpandable = item is NavigationSuiteItem.ExpandableItem

    var groupStart = itemIndex
    while (groupStart > 0) {
        if (isExpandable && isExpanded(groupStart)) {
            break
        }
        val prev = itemList[groupStart - 1]
        if (prev is NavigationSuiteItem.HeaderItem) {
            break
        }
        val previousIsExpandable = prev is NavigationSuiteItem.ExpandableItem
        if (previousIsExpandable != isExpandable) {
            break
        }
        if (isExpandable && isExpanded(groupStart - 1)) {
            break
        }
        groupStart--
    }

    var groupEnd = itemIndex
    while (groupEnd < itemList.size - 1) {
        val next = itemList[groupEnd + 1]
        if (next is NavigationSuiteItem.HeaderItem) {
            break
        }
        val nextIsExpandable = next is NavigationSuiteItem.ExpandableItem
        if (nextIsExpandable != isExpandable) {
            break
        }
        if (isExpandable && isExpanded(groupEnd + 1)) {
            break
        }
        if (isExpandable && isExpanded(groupEnd)) {
            break
        }
        groupEnd++
    }

    return SegmentedGroupPosition(
        index = itemIndex - groupStart,
        size = groupEnd - groupStart + 1,
    )
}

@Composable
private fun NavigationSuiteItemGroup(
    provider: NavigationSuiteItemProvider,
    layoutType: NavigationSuiteType,
    actualLayoutType: NavigationSuiteType,
) {
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }
    DisposableEffect(provider.itemsCount) {
        expandedStates.keys.removeAll { it >= provider.itemsCount }
        onDispose {}
    }
    if (layoutType == NavigationSuiteType.NavigationBar) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            provider.itemList.forEachIndexed { index, item ->
                when (item) {
                    is NavigationSuiteItem.HeaderItem -> {
                        Text(
                            text = item.title,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }

                    else -> {
                        val groupPosition =
                            provider.segmentedGroupPosition(index) { expandedIndex ->
                                expandedStates[expandedIndex] == true
                            }
                        NavigationSuiteSegmentedItem(
                            item = item,
                            index = groupPosition.index,
                            itemsCount = groupPosition.size,
                            expanded = expandedStates[index] == true,
                            onExpandedChange = { expandedStates[index] = it },
                        )
                    }
                }
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            provider.itemList.forEachIndexed { index, item ->
                when (item) {
                    is NavigationSuiteItem.HeaderItem -> {
                        Text(
                            text = item.title,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 4.dp),
                        )
                    }

                    is NavigationSuiteItem.DefaultItem -> {
                        NavigationSuiteAdaptiveDefaultItem(
                            item = item,
                            actualLayoutType = actualLayoutType,
                            modifier =
                                Modifier.padding(
                                    vertical = 4.dp,
                                ),
                        )
                    }

                    is NavigationSuiteItem.ExpandableItem -> {
                        val groupPosition =
                            provider.segmentedGroupPosition(index) { expandedIndex ->
                                expandedStates[expandedIndex] == true
                            }
                        NavigationSuiteSegmentedItem(
                            item = item,
                            index = groupPosition.index,
                            itemsCount = groupPosition.size,
                            expanded = expandedStates[index] == true,
                            onExpandedChange = { expandedStates[index] = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.NavigationSuiteSegmentedItem(
    item: NavigationSuiteItem,
    index: Int,
    itemsCount: Int,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    when (item) {
        is NavigationSuiteItem.DefaultItem -> {
            NavigationSuiteSegmentedDefaultItem(
                item = item,
                index = index,
                itemsCount = itemsCount,
            )
        }

        is NavigationSuiteItem.ExpandableItem -> {
            NavigationSuiteSegmentedExpandableItem(
                item = item,
                index = index,
                itemsCount = itemsCount,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
            )
        }

        is NavigationSuiteItem.HeaderItem -> {
            Unit
        }
    }
}

@Composable
private fun NavigationSuiteSegmentedDefaultItem(
    item: NavigationSuiteItem.DefaultItem,
    index: Int,
    itemsCount: Int,
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (item.selected) 1.05f else 1.0f,
        animationSpec =
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        label = "segmented_nav_item_bounce",
    )
    SegmentedListItem(
        content = {
            item.label?.invoke()
        },
        onClick = item.onClick,
        shapes =
            ListItemDefaults.segmentedShapes2(
                index,
                itemsCount,
            ),
        leadingContent = {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }.clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        if (item.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                ) {
                    item.icon()
                }
            }
        },
        trailingContent = item.badge,
        modifier =
            item.modifier
                .padding(horizontal = 16.dp),
        contentPadding = contentPadding,
    )
}

@Composable
private fun ColumnScope.NavigationSuiteSegmentedExpandableItem(
    item: NavigationSuiteItem.ExpandableItem,
    index: Int,
    itemsCount: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = expanded,
        onCheckedChange = onExpandedChange,
        shapes =
            ListItemDefaults.segmentedShapes2(
                index,
                itemsCount,
            ),
        content = {
            item.label?.invoke()
        },
        leadingContent = item.icon,
        trailingContent = {
            if (item.children.isNotEmpty()) {
                FAIcon(
                    imageVector =
                        if (expanded) {
                            FontAwesomeIcons.Solid.CaretUp
                        } else {
                            FontAwesomeIcons.Solid.CaretDown
                        },
                    contentDescription =
                        stringResource(
                            if (expanded) R.string.expanded else R.string.collapsed,
                        ),
                )
            }
        },
        modifier =
            item.modifier
                .padding(horizontal = 16.dp),
    )
    AnimatedVisibility(expanded) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item.children.forEachIndexed { subIndex, subItem ->
                require(subItem is NavigationSuiteItem.DefaultItem) {
                    "Only default items are supported as expandable children. Found: ${subItem::class.simpleName}"
                }
                NavigationSuiteSegmentedDefaultItem(
                    item = subItem,
                    index = subIndex,
                    itemsCount = item.children.size,
                    contentPadding =
                        ListItemDefaults.ContentPadding
                            .plus(PaddingValues(start = 16.dp)),
                )
            }
        }
    }
}

@Composable
private fun NavigationSuiteAdaptiveDefaultItem(
    item: NavigationSuiteItem.DefaultItem,
    actualLayoutType: NavigationSuiteType,
    modifier: Modifier = Modifier,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (item.selected) 1.08f else 1.0f,
        animationSpec =
            androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        label = "nav_item_bounce",
    )
    androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem(
        modifier = item.modifier.then(modifier),
        selected = item.selected,
        onClick = item.onClick,
        icon = {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                item.icon()
            }
        },
        enabled = item.enabled,
        label = item.label,
        interactionSource = item.interactionSource,
        navigationSuiteType = actualLayoutType,
        badge = item.badge,
    )
}

private sealed interface NavigationSuiteItem {
    data class HeaderItem(
        val title: String,
    ) : NavigationSuiteItem

    data class DefaultItem(
        val selected: Boolean,
        val onClick: () -> Unit,
        val icon: @Composable () -> Unit,
        val modifier: Modifier,
        val enabled: Boolean,
        val label: @Composable (() -> Unit)?,
        val alwaysShowLabel: Boolean,
        val badge: (@Composable () -> Unit)?,
        val colors: NavigationSuiteItemColors?,
        val interactionSource: MutableInteractionSource?,
        val onLongClick: (() -> Unit)? = null,
        val expandedContent: (@Composable (onReselect: () -> Unit) -> Unit)? = null,
    ) : NavigationSuiteItem

    data class ExpandableItem(
        val icon: @Composable () -> Unit,
        val modifier: Modifier,
        val enabled: Boolean,
        val label: @Composable (() -> Unit)?,
        val alwaysShowLabel: Boolean,
        val colors: NavigationSuiteItemColors?,
        val children: MutableVector<NavigationSuiteItem>,
    ) : NavigationSuiteItem
}

/** The scope associated with the [NavigationSuiteScope]. */
@ExperimentalMaterial3AdaptiveNavigationSuiteApi
interface NavigationSuiteScope2 {
    /**
     * Inserts a semantic category header in drawer navigation.
     */
    fun header(title: String)

    /**
     * This function sets the parameters of the default Material navigation item to be used with the
     * Navigation Suite Scaffold. The item is called in [NavigationSuite], according to the
     * current [NavigationSuiteType].
     *
     * For specifics about each item component, see [NavigationBarItem], [NavigationRailItem], and
     * [NavigationDrawerItem].
     *
     * @param selected whether this item is selected
     * @param onClick called when this item is clicked
     * @param icon icon for this item, typically an [Icon]
     * @param modifier the [Modifier] to be applied to this item
     * @param enabled controls the enabled state of this item. When `false`, this component will not
     * respond to user input, and it will appear visually disabled and disabled to accessibility
     * services. Note: as of now, for [NavigationDrawerItem], this is always `true`.
     * @param label the text label for this item
     * @param alwaysShowLabel whether to always show the label for this item. If `false`, the label will
     * only be shown when this item is selected. Note: for [NavigationDrawerItem] this is always `true`
     * @param badge optional badge to show on this item
     * @param colors [NavigationSuiteItemColors] that will be used to resolve the colors used for this
     * item in different states.
     * @param interactionSource an optional hoisted [MutableInteractionSource] for observing and
     * emitting [Interaction]s for this item. You can use this to change the item's appearance
     * or preview the item in different states. Note that if `null` is provided, interactions will
     * still happen internally.
     */
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        badge: (@Composable () -> Unit)? = null,
        colors: NavigationSuiteItemColors? = null,
        interactionSource: MutableInteractionSource? = null,
        onLongClick: (() -> Unit)? = null,
        expandedContent: (@Composable (onReselect: () -> Unit) -> Unit)? = null,
    )

    fun expandableItem(
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        colors: NavigationSuiteItemColors? = null,
        children: NavigationSuiteScope2.() -> Unit,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
private class NavigationSuiteScopeImpl :
    NavigationSuiteScope2,
    NavigationSuiteItemProvider {
    override fun header(title: String) {
        itemList.add(NavigationSuiteItem.HeaderItem(title))
    }

    override fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        label: @Composable (() -> Unit)?,
        alwaysShowLabel: Boolean,
        badge: (@Composable () -> Unit)?,
        colors: NavigationSuiteItemColors?,
        interactionSource: MutableInteractionSource?,
        onLongClick: (() -> Unit)?,
        expandedContent: (@Composable (onReselect: () -> Unit) -> Unit)?,
    ) {
        itemList.add(
            NavigationSuiteItem.DefaultItem(
                selected = selected,
                onClick = onClick,
                icon = icon,
                modifier = modifier,
                enabled = enabled,
                label = label,
                alwaysShowLabel = alwaysShowLabel,
                badge = badge,
                colors = colors,
                interactionSource = interactionSource,
                onLongClick = onLongClick,
                expandedContent = expandedContent,
            ),
        )
    }

    override fun expandableItem(
        icon: @Composable (() -> Unit),
        modifier: Modifier,
        enabled: Boolean,
        label: @Composable (() -> Unit)?,
        alwaysShowLabel: Boolean,
        colors: NavigationSuiteItemColors?,
        children: NavigationSuiteScope2.() -> Unit,
    ) {
        itemList.add(
            NavigationSuiteItem.ExpandableItem(
                icon = icon,
                modifier = modifier,
                enabled = enabled,
                label = label,
                alwaysShowLabel = alwaysShowLabel,
                colors = colors,
                children =
                    NavigationSuiteScopeImpl()
                        .apply {
                            children.invoke(this)
                        }.itemList,
            ),
        )
    }

    override val itemList: MutableVector<NavigationSuiteItem> = mutableVectorOf()

    override val itemsCount: Int
        get() = itemList.size
}

@OptIn(ExperimentalMaterial3AdaptiveNavigationSuiteApi::class)
@Composable
private fun rememberStateOfItems(content: NavigationSuiteScope2.() -> Unit): State<NavigationSuiteItemProvider> {
    val latestContent = rememberUpdatedState(content)
    return remember {
        derivedStateOf { NavigationSuiteScopeImpl().apply(latestContent.value) }
    }
}

@Composable
private fun NavigationItemIcon(
    icon: @Composable () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    val iconContent = remember(icon) { movableContentOf(icon) }
    if (badge != null) {
        BadgedBox(badge = { badge.invoke() }) {
            iconContent.invoke()
        }
    } else {
        iconContent.invoke()
    }
}
