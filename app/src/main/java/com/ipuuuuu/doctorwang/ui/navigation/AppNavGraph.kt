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
    val navigateToLoginAndClearStack = {
        navController.navigate(Screen.Login.route) {
            popUpTo(0)
            launchSingleTop = true
        }
    }

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
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onChatClick = { navController.navigate(Screen.Chat.route) },
                onUploadClick = { navController.navigate(Screen.Upload.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onSessionExpired = navigateToLoginAndClearStack,
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen(
                onUploadClick = { navController.navigate(Screen.Upload.route) },
                onSessionExpired = navigateToLoginAndClearStack,
            )
        }
        composable(Screen.Upload.route) {
            UploadScreen(
                onUploadSuccess = { reportId ->
                    navController.navigate(Screen.ReportDetail.createRoute(reportId))
                },
                onSessionExpired = navigateToLoginAndClearStack,
            )
        }
        composable(
            route = Screen.ReportDetail.route,
            arguments = listOf(navArgument("reportId") { type = NavType.LongType }),
        ) {
            ReportDetailScreen(onSessionExpired = navigateToLoginAndClearStack)
        }
        composable(Screen.Profile.route) {
            HealthProfileScreen(onSessionExpired = navigateToLoginAndClearStack)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onLoggedOut = navigateToLoginAndClearStack,
            )
        }
    }
}
