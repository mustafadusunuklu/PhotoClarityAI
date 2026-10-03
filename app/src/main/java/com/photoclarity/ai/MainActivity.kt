package com.photoclarity.ai

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photoclarity.ai.core.media.PhotoAccess
import com.photoclarity.ai.core.media.PhotoAccessManager
import com.photoclarity.ai.ui.components.LocalPhotoAccess
import com.photoclarity.ai.ui.components.PhotoAccessBanner
import com.photoclarity.ai.ui.components.PhotoAccessUi
import com.photoclarity.ai.ui.scan.ScanResultHolder
import javax.inject.Inject
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
    @Inject lateinit var photoAccess: PhotoAccessManager
    private val photoPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        photoAccess.refresh(this, selectionMayHaveChanged = true)
    }

    override fun onResume() {
        super.onResume()
        photoAccess.refresh(this)
    }

    private fun requestPhotoAccess() {
        if (photoAccess.state.value.needsSettings || photoAccess.state.value.access == PhotoAccess.FULL) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            photoAccess.markRequested()
            photoPermissionRequest.launch(PhotoAccessManager.permissions())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PhotoClarityTheme(darkTheme = true) {
                val access by photoAccess.state.collectAsStateWithLifecycle()
                LaunchedEffect(access.revision, access.error) {
                    if (ScanResultHolder.accessRevision != null &&
                        (ScanResultHolder.accessRevision != access.revision || access.error != null)) {
                        ScanResultHolder.groups = emptyList()
                        ScanResultHolder.error = "Fotoğraf erişimi değişti. Güncel erişimle yeniden tarayın."
                    }
                }
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
                    if (access.access != PhotoAccess.DENIED) Screen.Dashboard.route
                    else Screen.Onboarding.route
                }


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
                    CompositionLocalProvider(LocalPhotoAccess provides PhotoAccessUi(access, ::requestPhotoAccess)) {
                        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                            .consumeWindowInsets(WindowInsets.safeDrawing)) {
                            PhotoAccessBanner()
                            Box(Modifier.weight(1f)) {
                                PhotoClarityNavGraph(
                                    navController = navController,
                                    startDestination = startDestination,
                                    onOpenDrawer = openDrawer,
                                    onNavigate = { route -> navigateTo(route) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
