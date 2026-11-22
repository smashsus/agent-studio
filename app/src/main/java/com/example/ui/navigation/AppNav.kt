package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.Brain
import com.adamglin.phosphoricons.fill.ClockCounterClockwise
import com.adamglin.phosphoricons.fill.Folder
import com.adamglin.phosphoricons.fill.House
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.Folder
import com.adamglin.phosphoricons.regular.House
import androidx.navigation.toRoute
import com.example.ui.MainViewModel
import com.example.ui.components.HtmlSandboxPreviewDialog
import com.example.ui.components.SwissDivider
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.FilesAndSandboxScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import kotlinx.serialization.Serializable

@Serializable data object WorkspaceRoute
@Serializable data class ChatRoute(val prefillPrompt: String = "")
@Serializable data object ActivityRoute
@Serializable data object MemoryRoute
@Serializable data object FilesRoute
@Serializable data object AuthRoute
@Serializable data object SettingsRoute

private data class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topLevelDestinations = listOf(
    TopLevelDestination(
        WorkspaceRoute,
        "Home",
        PhosphorIcons.Fill.House,
        PhosphorIcons.Regular.House,
        "nav_workspace"
    ),
    TopLevelDestination(
        ActivityRoute,
        "Activity",
        PhosphorIcons.Fill.ClockCounterClockwise,
        PhosphorIcons.Regular.ClockCounterClockwise,
        "nav_activity"
    ),
    TopLevelDestination(
        MemoryRoute,
        "Memory",
        PhosphorIcons.Fill.Brain,
        PhosphorIcons.Regular.Brain,
        "nav_memory"
    ),
    TopLevelDestination(
        FilesRoute,
        "Files",
        PhosphorIcons.Fill.Folder,
        PhosphorIcons.Regular.Folder,
        "nav_files"
    )
)

@Composable
fun AgentStudioAppRoot(viewModel: MainViewModel) {
    val tokens = LocalSwissTokens.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val htmlPreview by viewModel.htmlPreviewContent.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openChatEvents.collect {
            navController.navigate(ChatRoute()) {
                launchSingleTop = true
            }
        }
    }

    val navigateToTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isTopLevel = topLevelDestinations.any { dest ->
        currentDestination?.hasRoute(dest.route::class) == true
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(tokens.background)) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            containerColor = tokens.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail && isTopLevel) {
                    SwissBottomNavigationBar(
                        currentDestination = currentDestination,
                        onSelect = navigateToTopLevel
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && isTopLevel) {
                    NavigationRail(containerColor = tokens.surface1) {
                        topLevelDestinations.forEach { dest ->
                            val selected = currentDestination?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = { Text(dest.label, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = tokens.primaryAccent,
                                    selectedTextColor = tokens.primaryText,
                                    indicatorColor = tokens.primaryAccent.copy(alpha = 0.14f),
                                    unselectedIconColor = tokens.secondaryText,
                                    unselectedTextColor = tokens.secondaryText
                                ),
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = WorkspaceRoute,
                    enterTransition = {
                        fadeIn(tween(SwissMotion.screenDurationMs, easing = SwissMotion.standardEasing))
                    },
                    exitTransition = {
                        fadeOut(tween(SwissMotion.screenDurationMs, easing = SwissMotion.standardEasing))
                    },
                    modifier = Modifier.weight(1f).fillMaxSize()
                ) {
                    composable<WorkspaceRoute> {
                        WorkspaceScreen(
                            viewModel = viewModel,
                            onOpenChat = { prefill ->
                                navController.navigate(ChatRoute(prefillPrompt = prefill)) {
                                    launchSingleTop = true
                                }
                            },
                            onNavigateToAuth = { navController.navigate(AuthRoute) },
                            onNavigateToSettings = { navController.navigate(SettingsRoute) },
                            onNavigateToActivity = { navigateToTopLevel(ActivityRoute) },
                            onNavigateToFiles = { navigateToTopLevel(FilesRoute) }
                        )
                    }
                    composable<ChatRoute> { entry ->
                        val route = entry.toRoute<ChatRoute>()
                        ChatScreen(
                            viewModel = viewModel,
                            initialDraft = route.prefillPrompt,
                            onBack = { navController.popBackStack() },
                            onNavigateToAuth = { navController.navigate(AuthRoute) }
                        )
                    }
                    composable<ActivityRoute> {
                        ActivityScreen(viewModel = viewModel)
                    }
                    composable<MemoryRoute> {
                        MemoryScreen(viewModel = viewModel)
                    }
                    composable<FilesRoute> {
                        FilesAndSandboxScreen(viewModel = viewModel)
                    }
                    composable<AuthRoute> {
                        AuthScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate(SettingsRoute) }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    htmlPreview?.let { (title, html) ->
        HtmlSandboxPreviewDialog(
            title = title,
            htmlContent = html,
            onDismiss = { viewModel.closeHtmlPreview() }
        )
    }
}

@Composable
private fun SwissBottomNavigationBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelect: (Any) -> Unit
) {
    val tokens = LocalSwissTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.surface1)
            .navigationBarsPadding()
    ) {
        SwissDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SwissGeometry.bottomNavHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            topLevelDestinations.forEach { dest ->
                val selected = currentDestination?.hasRoute(dest.route::class) == true
                val pillColor by animateColorAsState(
                    targetValue = if (selected) tokens.primaryAccent.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = tween(SwissMotion.fadeDurationMs),
                    label = "nav_pill_${dest.label}"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (selected) tokens.primaryAccent else tokens.secondaryText,
                    animationSpec = tween(SwissMotion.fadeDurationMs),
                    label = "nav_tint_${dest.label}"
                )
                val labelColor = if (selected) tokens.primaryText else tokens.secondaryText

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
                        .clip(CircleShape)
                        .clickable { onSelect(dest.route) }
                        .padding(vertical = 6.dp)
                        .testTag(dest.testTag)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .width(56.dp)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(pillColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = labelColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
