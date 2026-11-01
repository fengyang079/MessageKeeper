package com.messagekeeper.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.messagekeeper.ui.screens.analytics.AnalyticsScreen
import com.messagekeeper.ui.screens.backup.BackupScreen
import com.messagekeeper.ui.screens.chat.ChatScreen
import com.messagekeeper.ui.screens.conversations.ConversationsScreen
import com.messagekeeper.ui.screens.settings.SettingsScreen
import com.messagekeeper.ui.screens.specialalert.SpecialAlertScreen
import com.messagekeeper.ui.screens.trash.DeletedHistoryScreen
import com.messagekeeper.ui.theme.AccentOrange

object Routes {
    const val CONVERSATIONS   = "conversations"
    const val ANALYTICS       = "analytics"
    const val CHAT            = "chat/{conversationId}"
    const val SETTINGS        = "settings"
    const val SPECIAL_ALERTS  = "special_alerts"
    const val BACKUPS         = "backups"
    const val DELETED_HISTORY = "deleted_history"

    fun chat(conversationId: Long) = "chat/$conversationId"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.CONVERSATIONS, "消息", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    BottomNavItem(Routes.ANALYTICS,     "情报", Icons.Rounded.Insights,   Icons.Outlined.Insights),
    BottomNavItem(Routes.SETTINGS,      "设置", Icons.Rounded.Settings,   Icons.Outlined.Settings),
)

private val tabRoutes = bottomNavItems.map { it.route }.toSet()

// ───────────────────── Material 3 Motion Tokens ─────────────────────
// 参考 m3.material.io/styles/motion：Emphasized 系列缓动 + 标准时长
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
private val Emphasized           = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

private const val DURATION_LONG   = 400   // 页面推入
private const val DURATION_MEDIUM = 300   // 页面退出 / 返回
private const val FADE_OUT        = 90    // Shared Axis / Fade Through 的淡出段
private const val FADE_IN         = 210   // 淡入段（延迟 90ms 开始，避免两页叠影）

/** 横向轴位移比例（M3 规范为 30dp，按屏宽比例近似，避免整页滑动的廉价感） */
private const val AXIS_OFFSET = 0.10f

/** 底部导航栏占位高度（M3 NavigationBar 固定 80dp） */
private val BottomBarHeight = 80.dp

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

// ── Fade Through：同级 Tab 切换（M3 推荐用于底部导航） ──
private val fadeThroughEnter: EnterTransition =
    fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate)) +
        scaleIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate), initialScale = 0.94f)

private val fadeThroughExit: ExitTransition =
    fadeOut(tween(FADE_OUT, easing = EmphasizedAccelerate))

// ── Shared Axis X：层级前进 ──
private val axisForwardEnter: EnterTransition =
    slideInHorizontally(tween(DURATION_LONG, easing = EmphasizedDecelerate)) { (it * AXIS_OFFSET).toInt() } +
        fadeIn(tween(FADE_IN, delayMillis = FADE_OUT, easing = EmphasizedDecelerate))

private val axisForwardExit: ExitTransition =
    slideOutHorizontally(tween(DURATION_LONG, easing = Emphasized)) { -(it * AXIS_OFFSET).toInt() } +
        fadeOut(tween(FADE_OUT, easing = EmphasizedAccelerate))

// ── 返回：下层页面从左侧轻微滑回 ──
private val axisBackEnter: EnterTransition =
    slideInHorizontally(tween(DURATION_MEDIUM, easing = EmphasizedDecelerate)) { -(it * AXIS_OFFSET).toInt() } +
        fadeIn(tween(FADE_IN, delayMillis = FADE_OUT / 2, easing = EmphasizedDecelerate))

// ── 返回：当前页面缩小成卡片并向右淡出（预测性返回手势会按手指进度拖动这段动画） ──
private val predictiveBackExit: ExitTransition =
    scaleOut(tween(DURATION_MEDIUM, easing = Emphasized), targetScale = 0.90f) +
        slideOutHorizontally(tween(DURATION_MEDIUM, easing = Emphasized)) { (it * AXIS_OFFSET).toInt() } +
        fadeOut(tween(DURATION_MEDIUM, easing = EmphasizedAccelerate))

@Composable
fun MessageKeeperNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in tabRoutes

    // 系统导航条高度（手势条 / 三键）
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // NavHost 铺满整个区域；底栏以覆盖层形式存在，显示/隐藏不会触发 NavHost 重新布局（避免进出聊天页时掉帧）
            NavHost(
                navController    = navController,
                startDestination = Routes.CONVERSATIONS,
                modifier         = Modifier.fillMaxSize(),
                enterTransition    = { if (isTabSwitch()) fadeThroughEnter else axisForwardEnter },
                exitTransition     = { if (isTabSwitch()) fadeThroughExit else axisForwardExit },
                popEnterTransition = { if (isTabSwitch()) fadeThroughEnter else axisBackEnter },
                popExitTransition  = { if (isTabSwitch()) fadeThroughExit else predictiveBackExit },
            ) {
                composable(Routes.CONVERSATIONS) {
                    TabContainer(BottomBarHeight + navBarInset) {
                        ConversationsScreen(
                            onConversationClick = { id ->
                                navController.navigate(Routes.chat(id)) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable(Routes.ANALYTICS) {
                    TabContainer(BottomBarHeight + navBarInset) { AnalyticsScreen() }
                }
                composable(Routes.SETTINGS) {
                    TabContainer(BottomBarHeight + navBarInset) {
                        SettingsScreen(
                            onNavigateToSpecialAlerts = {
                                navController.navigate(Routes.SPECIAL_ALERTS) { launchSingleTop = true }
                            },
                            onNavigateToBackups = {
                                navController.navigate(Routes.BACKUPS) { launchSingleTop = true }
                            },
                            onNavigateToDeletedHistory = {
                                navController.navigate(Routes.DELETED_HISTORY) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable(
                    route     = Routes.CHAT,
                    arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: 0L
                    TabContainer(navBarInset) {
                        ChatScreen(
                            conversationId = conversationId,
                            onBack         = { navController.popBackStack() }
                        )
                    }
                }
                composable(Routes.SPECIAL_ALERTS) {
                    TabContainer(navBarInset) {
                        SpecialAlertScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.BACKUPS) {
                    TabContainer(navBarInset) {
                        BackupScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.DELETED_HISTORY) {
                    TabContainer(navBarInset) {
                        DeletedHistoryScreen(onBack = { navController.popBackStack() })
                    }
                }
            }

            // ── 底部导航栏（覆盖层，随页面层级滑入/滑出） ──
            AnimatedVisibility(
                visible  = showBottomBar,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter    = slideInVertically(tween(DURATION_MEDIUM, easing = EmphasizedDecelerate)) { it } +
                    fadeIn(tween(DURATION_MEDIUM)),
                exit     = slideOutVertically(tween(DURATION_MEDIUM, easing = EmphasizedAccelerate)) { it } +
                    fadeOut(tween(FADE_IN)),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) return@NavigationBarItem
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = AccentOrange,
                                selectedTextColor   = AccentOrange,
                                indicatorColor      = AccentOrange.copy(alpha = 0.16f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 给每个页面预留底部空间，并铺上背景色（淡入淡出期间不会透出下层页面造成闪烁） */
@Composable
private fun TabContainer(bottomPadding: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = bottomPadding)
    ) {
        content()
    }
}
