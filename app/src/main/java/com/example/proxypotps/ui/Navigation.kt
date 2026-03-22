package com.example.proxypotps.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.proxypotps.ui.screens.NodesScreen
import com.example.proxypotps.ui.screens.SettingsScreen
import com.example.proxypotps.ui.screens.StressTestScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Nodes : Screen("nodes", "节点", Icons.Filled.Storage)
    data object StressTest : Screen("stress_test", "压测", Icons.Filled.Speed)
    data object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun ProxyPotNavHost(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Nodes.route,
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Nodes.route) { NodesScreen() }

            composable(Screen.StressTest.route) { StressTestScreen() }

            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(Screen.Nodes, Screen.StressTest, Screen.Settings)

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { screen ->
            NavigationBarItem(
                icon = { Icon(screen.icon, contentDescription = screen.title) },
                label = { Text(screen.title) },
                selected = currentRoute == screen.route,
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
