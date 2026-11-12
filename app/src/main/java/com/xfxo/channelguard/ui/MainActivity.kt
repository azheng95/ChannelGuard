package com.xfxo.channelguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.highcapable.yukihookapi.YukiHook
import com.xfxo.channelguard.R
import com.xfxo.channelguard.data.AppFilterPreferences
import com.xfxo.channelguard.data.PrefsHelper
import com.xfxo.channelguard.ui.theme.ChannelGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsHelper.initialize(applicationContext)
        AppFilterPreferences.initialize(applicationContext)
        val isModuleActive = YukiHook.Status.isModuleActive

        setContent {
            var showInactiveDialog by remember { mutableStateOf(!isModuleActive) }

            ChannelGuardTheme {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val rootRoutes = listOf("subscriptions", "appList", "settings")
                Scaffold(
                    bottomBar = {
                        if (route in rootRoutes) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = route == "subscriptions",
                                    onClick = { navigateToTab(navController, "subscriptions") },
                                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                                    label = { Text("订阅") }
                                )
                                NavigationBarItem(
                                    selected = route == "appList",
                                    onClick = { navigateToTab(navController, "appList") },
                                    icon = { Icon(Icons.Default.Android, contentDescription = null) },
                                    label = { Text("应用") }
                                )
                                NavigationBarItem(
                                    selected = route == "settings",
                                    onClick = { navigateToTab(navController, "settings") },
                                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    label = { Text("设置") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = "subscriptions",
                        modifier = Modifier.padding(padding)
                    ) {
                        composable("subscriptions") { SubscriptionScreen() }
                        composable("appList") { AppListScreen(navController = navController) }
                        composable(
                            route = "channelDetail/{pkg}",
                            arguments = listOf(navArgument("pkg") { type = NavType.StringType })
                        ) { entry ->
                            ChannelDetailScreen(entry.arguments?.getString("pkg").orEmpty(), navController)
                        }
                        composable("settings") { SettingsScreen(navController = navController) }
                    }
                }

                if (showInactiveDialog) {
                    AlertDialog(
                        onDismissRequest = { showInactiveDialog = false },
                        title = { Text(stringResource(R.string.app_name)) },
                        text = { Text(stringResource(R.string.module_not_active)) },
                        confirmButton = {
                            TextButton(onClick = { showInactiveDialog = false }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun navigateToTab(navController: androidx.navigation.NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
