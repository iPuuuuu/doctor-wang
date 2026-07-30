package com.ipuuuuu.doctorwang.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Chat : Screen("chat")
    data object Upload : Screen("upload")
    data object ReportDetail : Screen("report_detail")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
}
