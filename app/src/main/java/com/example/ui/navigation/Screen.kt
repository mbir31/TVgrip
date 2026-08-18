package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Remote : Screen("remote")
    data object Keyboard : Screen("keyboard")
    data object Gamepad : Screen("gamepad")
    data object PairingWizard : Screen("pairing_wizard")
    data object DeviceList : Screen("device_list")
    data object CompatibilityReport : Screen("compatibility_report")
    data object Settings : Screen("settings")
    data object Diagnostics : Screen("diagnostics")
    data object About : Screen("about")
}
