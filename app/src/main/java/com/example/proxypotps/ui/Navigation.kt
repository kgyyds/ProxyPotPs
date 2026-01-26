package com.example.proxypotps.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.proxypotps.ui.screens.NodesScreen
import com.example.proxypotps.ui.screens.SettingsScreen
import com.example.proxypotps.ui.screens.WorkScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Nodes : Screen("nodes", "节点详情", Icons.Filled.Storage)
    data object Work : Screen("work", "工作详情", Icons.AutoMirrored.Filled.List)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun ProxyPotNavHost(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val navController = rememberNavController()
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screen.Nodes.route
    ) {
        composable(Screen.Nodes.route) { NodesScreen() }
        composable(Screen.Work.route) { WorkScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
    BottomNavigationBar(navController = navController)
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Nodes, Screen.Work, Screen.Settings)
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
