package com.photoclarity.ai.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding   : Screen("onboarding")
    object Dashboard    : Screen("dashboard")
    object Scan         : Screen("scan")
    object Results      : Screen("results")
    object GroupDetail  : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: String) = "group_detail/$groupId"
    }
    object Settings        : Screen("settings")
    object Profile         : Screen("profile")
    object Photos          : Screen("photos")
    object ScanHistory     : Screen("scan_history")
    object About           : Screen("about")
    object Favorites       : Screen("favorites")
    object SmartSuggestions: Screen("smart_suggestions")
    object Trash           : Screen("trash")
}
