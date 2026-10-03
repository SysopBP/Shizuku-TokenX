package moe.shizuku.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.component.TokenXBackground
import moe.shizuku.manager.ui.screen.AppsScreen
import moe.shizuku.manager.ui.screen.AppearanceStudioScreen
import moe.shizuku.manager.ui.screen.HomeScreen
import moe.shizuku.manager.ui.screen.AppToggleFeature
import moe.shizuku.manager.ui.screen.IntentsScreen
import moe.shizuku.manager.ui.screen.LabsScreen
import moe.shizuku.manager.ui.screen.LabsToggleScreen
import moe.shizuku.manager.ui.screen.ManageScreen
import moe.shizuku.manager.ui.screen.PermissionsScreen
import moe.shizuku.manager.ui.screen.SettingsScreen
import moe.shizuku.manager.ui.screen.ShellScreen
import moe.shizuku.manager.ui.screen.StealthScreen
import moe.shizuku.manager.ui.screen.TerminalScreen
import moe.shizuku.manager.ui.screen.TokenXControlCenterScreen
import moe.shizuku.manager.ui.screen.TokenXGuideScreen
import moe.shizuku.manager.ui.theme.LocalAmoledTheme
import moe.shizuku.manager.ui.theme.FloatingBarStyle
import moe.shizuku.manager.ui.theme.FloatingBarShape
import moe.shizuku.manager.ui.theme.FloatingBarSelection
import moe.shizuku.manager.ui.theme.TokenXAppearanceKeys
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.ui.theme.ShizukuTheme

/**
 * A secondary screen shown on top of the tab pager.
 *
 * The first two are what the Labs tab opens, and they are the reason it exists: an app's ops
 * and a shell are things you go to, not places you live, and giving each of them a tab meant
 * the bar spent its whole width on five icons while the two screens behind two of them were
 * mostly empty when you arrived.
 */
enum class Detail { APP_OPS, SHELL, FIREWALL, AUTOSTART, STEALTH, TERMINAL, INTENTS, PERMISSIONS, APPEARANCE, TOKENX, GUIDE }

/**
 * On wide windows (tablets, foldables, desktop mode, mirrored displays) a
 * single-column layout stretched edge to edge looks sparse, so the content is
 * capped at this width and centred. On a phone the window is narrower than the
 * cap, so this has no effect.
 */
private val MaxContentWidth = 600.dp



private data class Tab(
    val label: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    Tab(R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Tab(R.string.tab_apps, Icons.Filled.Apps, Icons.Outlined.Apps),
    // Labs holds the things that are gone to rather than lived in: the app-ops list and the
    // shell to begin with, and whatever else turns out to belong there. Each was a tab of its
    // own before, which is a lot of the bar for two screens that are mostly a list you read
    // once, and it also made the shell's own session the price of switching to Settings.
    Tab(R.string.tab_labs, Icons.Filled.Science, Icons.Outlined.Science),
    Tab(R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun ShizukuApp() {
    ShizukuTheme {
        // Detail screens are shown outside the Scaffold, so wrap everything in a
        // Surface otherwise LocalContentColor falls back to black and plain
        // Text becomes unreadable in dark themes.
        TokenXBackground {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
            // Apply the status bar inset exactly once for every screen: the app
            // bars themselves have no insets, and the Scaffold opts out too.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            ) {
                // Both the open detail and the pager live here, above the switch between
                // the two, for two reasons: the pager used to be remembered inside the tab
                // layout, so opening a detail threw it away and closing the detail started
                // again on the first tab (back from a Settings screen landed on Home). And
                // neither was saveable, so a rotation dropped both and did the same thing.
                var detail by rememberSaveable { mutableStateOf<Detail?>(null) }
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                val current = detail

                if (current != null) {
                    BackHandler { detail = null }
                    CenteredContent(
                        // A detail screen has no tab bar under it, so it is the one that has
                        // to keep clear of the navigation bar itself. The tab layout adds
                        // that inset to its own bottom padding; a detail had none, which on
                        // a device with navigation buttons put the shell's input row under
                        // them.
                        modifier = Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        )
                    ) {
                        when (current) {
                            // Both get the bar's padding of zero: a detail has no floating
                            // bar under it to keep clear of, and this wrapper already keeps
                            // them off the navigation bar.
                            Detail.APP_OPS -> ManageScreen(
                                bottomPadding = 0.dp,
                                onBack = { detail = null }
                            )

                            Detail.SHELL -> ShellScreen(bottomPadding = 0.dp, onBack = { detail = null })
                            Detail.FIREWALL -> LabsToggleScreen(
                                feature = AppToggleFeature.FIREWALL,
                                bottomPadding = 0.dp,
                                onBack = { detail = null }
                            )

                            Detail.AUTOSTART -> LabsToggleScreen(
                                feature = AppToggleFeature.AUTOSTART,
                                bottomPadding = 0.dp,
                                onBack = { detail = null }
                            )

                            Detail.STEALTH -> StealthScreen(onBack = { detail = null })
                            Detail.TERMINAL -> TerminalScreen(onBack = { detail = null })
                            Detail.INTENTS -> IntentsScreen(onBack = { detail = null })
                            Detail.PERMISSIONS -> PermissionsScreen(onBack = { detail = null })
                            Detail.APPEARANCE -> AppearanceStudioScreen()
                            Detail.TOKENX -> TokenXControlCenterScreen(onBack = { detail = null })
                            Detail.GUIDE -> TokenXGuideScreen(onBack = { detail = null })
                        }
                    }
                } else {
                    MainTabs(pagerState = pagerState, onOpenDetail = { detail = it })
                }
            }
            }
        }
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                // widthIn must come first: fillMaxWidth sets min == max, which
                // would defeat a later widthIn cap.
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainTabs(
    pagerState: PagerState,
    onOpenDetail: (Detail) -> Unit
) {
    val scope = rememberCoroutineScope()
    val appearancePrefs = ShizukuSettings.getPreferences()
    val floatingBarStyle = runCatching {
        FloatingBarStyle.valueOf(
            appearancePrefs.getString(TokenXAppearanceKeys.FLOATING_BAR_STYLE, FloatingBarStyle.FROSTED.name)!!
        )
    }.getOrDefault(FloatingBarStyle.FROSTED)
    val floatingBarOpacity = appearancePrefs
        .getFloat(TokenXAppearanceKeys.FLOATING_BAR_OPACITY, .82f)
        .coerceIn(.20f, 1f)
    val floatingBarShape = runCatching {
        FloatingBarShape.valueOf(appearancePrefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SHAPE, FloatingBarShape.ONE_UI.name)!!)
    }.getOrDefault(FloatingBarShape.ONE_UI)
    val floatingBarSelection = runCatching {
        FloatingBarSelection.valueOf(appearancePrefs.getString(TokenXAppearanceKeys.FLOATING_BAR_SELECTION, FloatingBarSelection.GLASS.name)!!)
    }.getOrDefault(FloatingBarSelection.GLASS)
    val floatingBarWidth = appearancePrefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_WIDTH, .86f).coerceIn(.62f, 1f)
    val floatingBarHeight = appearancePrefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_HEIGHT, 64f).coerceIn(52f, 78f)
    val floatingBarBottomGap = appearancePrefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BOTTOM_GAP, 8f).coerceIn(0f, 28f)
    val floatingBarBorder = appearancePrefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_BORDER, .18f).coerceIn(0f, .5f)
    val floatingBarElevation = appearancePrefs.getFloat(TokenXAppearanceKeys.FLOATING_BAR_ELEVATION, 8f).coerceIn(0f, 18f)

    // Whether the app has settled enough to read the lists nobody is looking at yet. Both app
    // lists are expensive to read - six hundred packages each - and a pager composes the page
    // being dragged in, so reading on composition spent swipes on them. Reading when the tab
    // is landed on fixed the swipe and made the first visit wait instead, which is what this
    // takes back: shortly after launch, while nobody is touching anything, the two lists read
    // themselves, so the swipe is still cheap and the tab is already full when it is opened.
    var warmUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        warmUp = true
    }

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )
    // The bar's height as a constant, rather than as something it reports back. Measuring it
    // and feeding that measurement into every page as bottom padding made the pages re-lay out
    // whenever the bar re-measured mid-animation — a scroll, a tab change — which is what read
    // as the content bouncing and left a gap the size of the bar's largest frame.
    val bottomPadding = FloatingToolbarDefaults.ContainerSize +
        FloatingToolbarDefaults.ScreenOffset +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior)
    ) {
        CenteredContent {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // Every page stays composed. The pager otherwise builds a page only as it is
                // dragged in, which is what the app lists were reading on; keeping them all
                // alive is what lets the warm-up below reach them before anyone opens them,
                // and it is also what keeps a tab's contents across a visit - the android
                // tab bar's own pages are cheap to hold, and only the rows on screen are
                // ever built.
                beyondViewportPageCount = tabs.size
            ) { page ->
                // Each page is told whether it is the one on screen, because the pager
                // composes the page being dragged in as well. The two that read every
                // installed package - six hundred of them, twice over in Manage's case -
                // started that the moment they were half on screen, which is what made
                // swiping towards the shell stutter: the swipe passes straight through both
                // app lists on its way there. A page that is not the settled one now waits,
                // and loads when it is landed on.
                val active = page == pagerState.settledPage
                when (page) {
                    0 -> HomeScreen(bottomPadding = bottomPadding)
                    1 -> AppsScreen(
                        bottomPadding = bottomPadding,
                        active = active,
                        warmUp = warmUp
                    )
                    2 -> LabsScreen(bottomPadding = bottomPadding, onOpenDetail = onOpenDetail)
                    3 -> SettingsScreen(bottomPadding = bottomPadding, onOpenDetail = onOpenDetail)
                }
            }
        }

        // The bar's own band, and the fade the pages run into. It is drawn here rather than
        // by the pages for two reasons: it leaves with the bar, and it is already there
        // before a page has scrolled a long list sitting at its top still has rows under
        // the bar, which a scrim that waited for a scroll would leave with a hard edge.
        // A gradient and not a blur: blurring a scrolling page means drawing it into an
        // offscreen layer and re-blurring it every frame.
        // The entire bottom decoration must participate in the toolbar's exit scroll.
        // Previously only HorizontalFloatingToolbar translated, leaving this full-width
        // fade/band behind as an empty rounded bar after the controls hid.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                )
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            contentAlignment = Alignment.TopCenter
        ) {
            // The bar hides by moving down by its own height, and it stands clear of the
            // gesture area — so it came to rest exactly that gap above the bottom of the
            // screen with a strip of itself still showing. Clipping this box to its own edge
            // cuts that strip off, and leaves the library's own timing alone: the box is
            // exactly the bar's height, so at rest nothing is cut, and on the way down all of
            // it is.
            // Centre-aligned, and so is the band: a full-width box with the bar in it would
            // otherwise leave the bar sitting against its left edge.
            Box(
                modifier = Modifier.fillMaxWidth().clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                // Where each tab sits on the bar, so the selected pill can be drawn between
                // them: one shape that travels reads as a move, where a container colour that
                // appears on the new tab and leaves the old one reads as a flicker. The pill
                // is measured from the tab itself, so it is the tab's own size wherever the
                // bar's padding puts it.
                val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
                val selectedBounds = tabBounds[pagerState.currentPage]
                val pillSpec = spring<Float>(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
                val pillLeft by animateFloatAsState(
                    targetValue = selectedBounds?.left ?: 0f,
                    animationSpec = pillSpec,
                    label = "tabPillLeft"
                )
                val pillWidth by animateFloatAsState(
                    targetValue = selectedBounds?.width ?: 0f,
                    animationSpec = pillSpec,
                    label = "tabPillWidth"
                )
                val pillColor = when (floatingBarSelection) {
                    FloatingBarSelection.GLASS -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f)
                    FloatingBarSelection.TILE -> MaterialTheme.colorScheme.secondaryContainer
                    FloatingBarSelection.INDICATOR -> MaterialTheme.colorScheme.primary.copy(alpha = .30f)
                    FloatingBarSelection.MINIMAL -> Color.Transparent
                }
                val barShape = when (floatingBarShape) {
                    FloatingBarShape.ONE_UI -> androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                    FloatingBarShape.SQUIRCLE -> androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
                    FloatingBarShape.ROUNDED -> androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    FloatingBarShape.PILL -> CircleShape
                }
                val barSurface = when (floatingBarStyle) {
                    FloatingBarStyle.FLOATING -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = floatingBarOpacity)
                    FloatingBarStyle.FROSTED -> Color(0xFF101216).copy(alpha = floatingBarOpacity)
                    FloatingBarStyle.SOLID -> MaterialTheme.colorScheme.surfaceContainerHighest
                    FloatingBarStyle.CLEAR -> Color.Transparent
                }
                val barBorder = when (floatingBarStyle) {
                    FloatingBarStyle.FROSTED -> Color.White.copy(alpha = floatingBarBorder)
                    FloatingBarStyle.CLEAR -> Color.White.copy(alpha = floatingBarBorder * .55f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = floatingBarBorder.coerceAtLeast(.08f))
                }

                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier
                        .fillMaxWidth(floatingBarWidth)
                        .padding(bottom = floatingBarBottomGap.dp)
                        .graphicsLayer { shadowElevation = floatingBarElevation.dp.toPx(); shape = barShape; clip = false },
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = Color.Transparent,
                        toolbarContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(0.dp),
                    scrollBehavior = scrollBehavior
                ) {
                    Row(
                        modifier = Modifier
                            .height(floatingBarHeight.dp)
                            .background(
                                color = barSurface,
                                shape = barShape
                            )
                            .border(
                                width = 1.dp,
                                color = barBorder,
                                shape = barShape
                            )
                            .padding(FloatingToolbarDefaults.ContentPadding)
                            // Last in the chain on purpose: the padding sits outside the Row, so
                            // drawing here happens in the same space the tabs are placed in, and
                            // their measured bounds can be used as they are.
                            .drawWithContent {
                                val bounds = selectedBounds
                                if (bounds != null && pillWidth > 0f) {
                                    drawRoundRect(
                                        color = pillColor,
                                        topLeft = Offset(pillLeft, bounds.top),
                                        size = Size(pillWidth, bounds.height),
                                        cornerRadius = CornerRadius(bounds.height / 2f)
                                    )
                                }
                                drawContent()
                            },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val selected = pagerState.currentPage == index
                            // The icon's colour is animated rather than handed to the button's
                            // checked state, because the pill behind it is still travelling when
                            // the state flips: a flip would leave the icon dark on a pill that has
                            // not arrived yet.
                            val iconColor by animateColorAsState(
                                targetValue = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                animationSpec = MaterialTheme.motionScheme
                                    .defaultEffectsSpec<Color>(),
                                label = "tabIconColor"
                            )
                            ToggleButton(
                                checked = selected,
                                modifier = Modifier.onGloballyPositioned {
                                    tabBounds[index] = it.boundsInParent()
                                },
                            onCheckedChange = {
                                // Straight to the page, not through the ones between: the pager
                                // composes what it scrolls past, so going from Settings to Home
                                // started loading the Manage tab's six hundred apps on the way.
                                // Swiping still animates, because that is the gesture.
                                if (!selected) scope.launch { pagerState.scrollToPage(index) }
                            },
                                shapes = ToggleButtonShapes(
                                    shape = CircleShape,
                                    pressedShape = CircleShape,
                                    checkedShape = CircleShape
                                ),
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    // Transparent either way: the pill is drawn by the bar, so the
                                    // button must not draw a second one under it.
                                    containerColor = Color.Transparent,
                                    contentColor = iconColor,
                                    checkedContainerColor = Color.Transparent,
                                    checkedContentColor = iconColor
                                )
                            ) {
                                // Icons only, with the name kept for anyone reading it aloud: a
                                // label that grows out of the selected tab makes the bar wider
                                // and taller, and the pages under it move. The filled pill says
                                // which tab is current, and the outlined icon resolving into the
                                // filled one says the pill has arrived.
                                Crossfade(
                                    targetState = selected,
                                    animationSpec = MaterialTheme.motionScheme
                                        .defaultEffectsSpec<Float>(),
                                    label = "tabIcon"
                                ) { isSelected ->
                                    Icon(
                                        if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.label)
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
