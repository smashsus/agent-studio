package com.example.ui.navigation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.components.HtmlSandboxPreviewDialog
import com.example.ui.components.SwissDivider
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.FilesAndSandboxScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.LocalSwissTokens
import com.example.ui.theme.SwissGeometry
import com.example.ui.theme.SwissMotion
import kotlinx.serialization.Serializable

@Serializable data object WorkspaceRoute
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
    TopLevelDestination(WorkspaceRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_workspace"),
    TopLevelDestination(ActivityRoute, "Activity", Icons.Filled.History, Icons.Outlined.History, "nav_activity"),
    TopLevelDestination(MemoryRoute, "Memory", Icons.Filled.Memory, Icons.Outlined.Memory, "nav_memory"),
    TopLevelDestination(FilesRoute, "Files", Icons.Filled.Folder, Icons.Outlined.Folder, "nav_files")
)

@Composable
fun DeepSeekAgentAppRoot(viewModel: MainViewModel) {
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
                                        modifier = Modifier
                                            .size(SwissGeometry.iconSize)
                                            .alpha(if (selected) 1.0f else 0.65f)
                                    )
                                },
                                label = { Text(dest.label, fontSize = 11.sp) },
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
                            onNavigateToAuth = { navController.navigate(AuthRoute) },
                            onNavigateToSettings = { navController.navigate(SettingsRoute) },
                            onNavigateToActivity = { navigateToTopLevel(ActivityRoute) }
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
                val iconAlpha = if (selected) 1.00f else 0.65f
                val contentColor = if (selected) tokens.primaryText else tokens.secondaryText

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = SwissGeometry.minTouchTarget)
                        .clickable { onSelect(dest.route) }
                        .padding(vertical = 8.dp)
                        .testTag(dest.testTag)
                ) {
                    Box(
                        modifier = Modifier.padding(bottom = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = if (selected) tokens.primaryAccent else contentColor,
                            modifier = Modifier
                                .size(22.dp)
                                .alpha(iconAlpha)
                        )
                    }
                    Text(
                        text = dest.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = contentColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
