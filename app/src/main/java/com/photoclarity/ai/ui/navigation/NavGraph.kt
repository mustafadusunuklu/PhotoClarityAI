package com.photoclarity.ai.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.photoclarity.ai.ui.about.AboutScreen
import com.photoclarity.ai.ui.dashboard.DashboardScreen
import com.photoclarity.ai.ui.favorites.FavoritesScreen
import com.photoclarity.ai.ui.history.ScanHistoryScreen
import com.photoclarity.ai.ui.onboarding.OnboardingScreen
import com.photoclarity.ai.ui.photos.PhotosScreen
import com.photoclarity.ai.ui.profile.ProfileScreen
import com.photoclarity.ai.ui.results.GroupDetailScreen
import com.photoclarity.ai.ui.results.ResultsScreen
import com.photoclarity.ai.ui.scan.ScanScreen
import com.photoclarity.ai.ui.settings.SettingsScreen
import com.photoclarity.ai.ui.suggestions.SmartSuggestionsScreen
import com.photoclarity.ai.ui.trash.TrashScreen

@Composable
fun PhotoClarityNavGraph(
    navController: NavHostController,
    startDestination: String,
    onOpenDrawer: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination,
        enterTransition  = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280))
        },
        exitTransition   = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(280))
        },
        popEnterTransition  = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280))
        },
        popExitTransition   = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(280))
        }
    ) {

        // ── Onboarding ─────────────────────────────────────────────────────────
        composable(
            route          = Screen.Onboarding.route,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition  = { fadeOut(tween(400)) }
        ) {
            OnboardingScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard ──────────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onOpenDrawer  = onOpenDrawer,
                onStartScan   = { navController.navigate(Screen.Scan.route) },
                onOpenResults = { navController.navigate(Screen.Results.route) },
                onNavigate    = onNavigate
            )
        }

        // ── Photos ─────────────────────────────────────────────────────────────
        composable(Screen.Photos.route) {
            PhotosScreen(
                onOpenDrawer  = onOpenDrawer,
                onStartScan   = { navController.navigate(Screen.Scan.route) },
                onOpenResults = { navController.navigate(Screen.Results.route) },
                onNavigate    = onNavigate
            )
        }

        // ── Scan ───────────────────────────────────────────────────────────────
        composable(Screen.Scan.route) {
            ScanScreen(
                onScanComplete = {
                    navController.navigate(Screen.Results.route) {
                        popUpTo(Screen.Scan.route) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        // ── Results ────────────────────────────────────────────────────────────
        composable(Screen.Results.route) {
            ResultsScreen(
                onGroupClick = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Group Detail ───────────────────────────────────────────────────────
        composable(
            route     = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupDetailScreen(
                groupId = groupId,
                onBack  = { navController.popBackStack() }
            )
        }

        // ── Settings ───────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        // ── Profile ────────────────────────────────────────────────────────────
        composable(
            route          = Screen.Profile.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition  = { fadeOut(tween(300)) }
        ) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }

        // ── Scan History ───────────────────────────────────────────────────────
        composable(Screen.ScanHistory.route) {
            ScanHistoryScreen(onBack = { navController.popBackStack() })
        }

        // ── About ──────────────────────────────────────────────────────────────
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // ── Favorites ──────────────────────────────────────────────────────────
        composable(Screen.Favorites.route) {
            FavoritesScreen(onBack = { navController.popBackStack() })
        }

        // ── Smart Suggestions ──────────────────────────────────────────────────
        composable(Screen.SmartSuggestions.route) {
            SmartSuggestionsScreen(
                onStartScan = { navController.navigate(Screen.Scan.route) },
                onBack      = { navController.popBackStack() }
            )
        }

        // ── Trash ──────────────────────────────────────────────────────────────
        composable(Screen.Trash.route) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
    }
}
