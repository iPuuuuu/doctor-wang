package com.ipuuuuu.doctorwang.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ipuuuuu.doctorwang.ui.screen.auth.LoginScreen
import com.ipuuuuu.doctorwang.ui.screen.chat.ChatScreen
import com.ipuuuuu.doctorwang.ui.screen.home.HomeScreen
import com.ipuuuuu.doctorwang.ui.screen.profile.HealthProfileScreen
import com.ipuuuuu.doctorwang.ui.screen.report.ReportDetailScreen
import com.ipuuuuu.doctorwang.ui.screen.settings.SettingsScreen
import com.ipuuuuu.doctorwang.ui.screen.splash.SplashScreen
import com.ipuuuuu.doctorwang.ui.screen.upload.UploadScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onChatClick = { navController.navigate(Screen.Chat.route) },
                onUploadClick = { navController.navigate(Screen.Upload.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen(onUploadClick = { navController.navigate(Screen.Upload.route) })
        }
        composable(Screen.Upload.route) {
            UploadScreen(
                onUploadSuccess = { reportId ->
                    navController.navigate(Screen.ReportDetail.createRoute(reportId))
                },
            )
        }
        composable(
            route = Screen.ReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.LongType }),
        ) {
            ReportDetailScreen()
        }
        composable(Screen.Profile.route) {
            HealthProfileScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onLoggedOut = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
            )
        }
    }
}
