package com.sharkord.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sharkord.android.ui.home.HomeScreen
import com.sharkord.android.ui.login.LoginScreen
import com.sharkord.android.ui.settings.UserSettingsScreen
import com.sharkord.android.ui.theme.SharkordTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            SharkordTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    AppNavigation(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "login",
        modifier = modifier
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("user_settings")
                },
                onNavigateToServerSettings = {
                    navController.navigate("server_settings")
                },
                onNavigateToChannelSettings = { channelId ->
                    navController.navigate("channel_settings/$channelId")
                }
            )
        }
        composable("user_settings") {
            UserSettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable("server_settings") {
            com.sharkord.android.ui.settings.ServerSettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(
            route = "channel_settings/{channelId}",
            arguments = listOf(androidx.navigation.navArgument("channelId") { type = androidx.navigation.NavType.IntType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getInt("channelId") ?: return@composable
            com.sharkord.android.ui.settings.ChannelSettingsScreen(
                channelId = channelId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}