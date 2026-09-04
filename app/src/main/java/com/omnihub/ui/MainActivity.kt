package com.omnihub.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnihub.ui.screens.ChatScreen
import com.omnihub.ui.screens.CustomizeScreen
import com.omnihub.ui.screens.SettingsScreen
import com.omnihub.ui.theme.OmniHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniHubTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OmniHubNav()
                }
            }
        }
    }
}

@Composable
fun OmniHubNav() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "chat") {
        composable("chat") {
            ChatScreen(
                onOpenSettings = { navController.navigate("settings") },
                onOpenCustomize = { navController.navigate("customize") }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("customize") {
            CustomizeScreen(onBack = { navController.popBackStack() })
        }
    }
}
