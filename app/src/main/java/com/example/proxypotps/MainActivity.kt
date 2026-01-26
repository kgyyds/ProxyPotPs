package com.example.proxypotps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.example.proxypotps.ui.ProxyPotNavHost
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
    ProxyPotNavHost()
}
