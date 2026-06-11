package com.photoclarity.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.photoclarity.ai.ui.dashboard.DrawerContent
import com.photoclarity.ai.ui.navigation.PhotoClarityNavGraph
import com.photoclarity.ai.ui.navigation.Screen
import com.photoclarity.ai.ui.theme.PhotoClarityTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhotoClarityTheme(darkTheme = true) {
                val navController = rememberNavController()
                val drawerState   = rememberDrawerState(DrawerValue.Closed)
                val scope         = rememberCoroutineScope()

                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                val drawerEnabled = currentRoute in setOf(
                    Screen.Dashboard.route,
                    Screen.Photos.route
                )

                val startDestination = remember {
                    if (hasStoragePermission()) Screen.Dashboard.route
                    else Screen.Onboarding.route
                }

                // ── Navigate helper ─────────────────────────────────────────
                val navigateTo: (String) -> Unit = { route ->
                    Toast.makeText(this@MainActivity, "Navigate: $route", Toast.LENGTH_SHORT).show()
                    scope.launch { drawerState.close() }
                    try {
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "NAV ERROR: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                // ── Open drawer helper ──────────────────────────────────────
                val openDrawer: () -> Unit = {
                    Toast.makeText(this@MainActivity, "Drawer açılıyor", Toast.LENGTH_SHORT).show()
                    scope.launch { drawerState.open() }
                }

                ModalNavigationDrawer(
                    drawerState     = drawerState,
                    gesturesEnabled = drawerEnabled,
                    drawerContent   = {
                        DrawerContent(
                            currentRoute = currentRoute,
                            onNavigate   = { route -> navigateTo(route) },
                            onClose      = { scope.launch { drawerState.close() } }
                        )
                    }
                ) {
                    PhotoClarityNavGraph(
                        navController    = navController,
                        startDestination = startDestination,
                        onOpenDrawer     = openDrawer,
                        onNavigate       = { route -> navigateTo(route) }
                    )
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
