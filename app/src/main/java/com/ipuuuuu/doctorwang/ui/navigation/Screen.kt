package com.ipuuuuu.doctorwang.ui.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Chat : Screen("chat")
    data object Upload : Screen("upload")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
    data object ReportDetail : Screen("report_detail/{reportId}") {
        fun createRoute(reportId: Long): String = "report_detail/$reportId"
    }
}
