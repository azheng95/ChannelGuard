package com.xfxo.channelguard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.highcapable.yukihookapi.YukiHookAPI
import com.xfxo.channelguard.R
import com.xfxo.channelguard.data.PrefsHelper
import com.xfxo.channelguard.ui.theme.ChannelGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsHelper.initialize(applicationContext)
        val isModuleActive = YukiHookAPI.Status.isXposedModuleActive

        setContent {
            var showInactiveDialog by remember { mutableStateOf(!isModuleActive) }

            ChannelGuardTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "appList") {
                    composable("appList") {
                        AppListScreen(navController = navController)
                    }
                    composable(
                        route = "channelDetail/{pkg}",
                        arguments = listOf(navArgument("pkg") { type = NavType.StringType })
                    ) { entry ->
                        ChannelDetailScreen(
                            pkg = entry.arguments?.getString("pkg").orEmpty(),
                            navController = navController
                        )
                    }
                    composable("settings") {
                        SettingsScreen(navController = navController)
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
