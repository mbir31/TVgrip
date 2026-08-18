package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feature.about.AboutScreen
import com.example.feature.devices.DeviceListScreen
import com.example.feature.diagnostics.DiagnosticsScreen
import com.example.feature.gamepad.GamepadScreen
import com.example.feature.home.HomeScreen
import com.example.feature.keyboard.KeyboardScreen
import com.example.feature.pairing.PairingWizardScreen
import com.example.feature.remote.RemoteScreen
import com.example.feature.report.CompatibilityReportScreen
import com.example.feature.settings.SettingsScreen
import com.example.ui.navigation.Screen
import com.example.ui.theme.GripBlack
import com.example.ui.theme.TVGripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TVGripTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GripBlack),
                    color = GripBlack
                ) {
                    TVGripAppNavHost()
                }
            }
        }
    }
}

@Composable
fun TVGripAppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRemote = { navController.navigate(Screen.Remote.route) },
                onNavigateToKeyboard = { navController.navigate(Screen.Keyboard.route) },
                onNavigateToGamepad = { navController.navigate(Screen.Gamepad.route) },
                onNavigateToPairing = { navController.navigate(Screen.PairingWizard.route) },
                onNavigateToDevices = { navController.navigate(Screen.DeviceList.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                onNavigateToAbout = { navController.navigate(Screen.About.route) }
            )
        }

        composable(Screen.Remote.route) {
            RemoteScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToKeyboard = { navController.navigate(Screen.Keyboard.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Keyboard.route) {
            KeyboardScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Gamepad.route) {
            GamepadScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PairingWizard.route) {
            PairingWizardScreen(
                onNavigateBack = { navController.popBackStack() },
                onPairingComplete = {
                    navController.navigate(Screen.Remote.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.DeviceList.route) {
            DeviceListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPairing = { navController.navigate(Screen.PairingWizard.route) },
                onNavigateToReport = { deviceId ->
                    navController.navigate("${Screen.CompatibilityReport.route}/$deviceId")
                }
            )
        }

        composable(
            route = "${Screen.CompatibilityReport.route}/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId")
            CompatibilityReportScreen(
                deviceId = deviceId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CompatibilityReport.route) {
            CompatibilityReportScreen(
                deviceId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
