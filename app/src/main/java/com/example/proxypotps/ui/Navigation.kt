package com.example.proxypotps.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.proxypotps.ui.screens.NodesScreen
import com.example.proxypotps.ui.screens.JobDetailScreen
import com.example.proxypotps.ui.screens.SettingsScreen
import com.example.proxypotps.ui.screens.WorkScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Nodes : Screen("nodes", "节点详情", Icons.Filled.Storage)
    data object Work : Screen("work", "工作详情", Icons.AutoMirrored.Filled.List)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)

    data object JobDetail : Screen("jobDetail/{taskId}", "工作详情", Icons.AutoMirrored.Filled.List) {
        fun createRoute(taskId: Long) = "jobDetail/$taskId"
    }
}

@Composable
fun ProxyPotNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screen.Nodes.route
    ) {
        composable(Screen.Nodes.route) { NodesScreen() }
        composable(Screen.Work.route) {
            WorkScreen(onJobClick = { taskId -> navController.navigate(Screen.JobDetail.createRoute(taskId)) })
        }
        composable(Screen.Settings.route) { SettingsScreen() }
        composable(
            route = Screen.JobDetail.route,
            arguments = listOf(navArgument("taskId") { type = androidx.navigation.NavType.LongType })
        ) {
            JobDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Nodes, Screen.Work, Screen.Settings)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { screen ->
            val selected = when (screen) {
                Screen.Work ->
                    currentRoute == Screen.Work.route ||
                        (currentRoute?.startsWith("jobDetail/") == true)
                else -> currentRoute == screen.route
            }

            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = selected,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}