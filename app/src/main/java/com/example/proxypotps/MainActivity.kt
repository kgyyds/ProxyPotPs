package com.example.proxypotps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.proxypotps.ui.BottomNavigationBar
import com.example.proxypotps.ui.Screen
import com.example.proxypotps.ui.screens.NodesScreen
import com.example.proxypotps.ui.screens.SettingsScreen
import com.example.proxypotps.ui.screens.WorkScreen
import com.example.proxypotps.ui.theme.ProxyPotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProxyPotTheme {
                ProxyPotApp()
            }
        }
    }
}

@Composable
private fun ProxyPotApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController = navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Nodes.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Nodes.route) { NodesScreen() }
            composable(Screen.Work.route) { WorkScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
