package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ApiVaultScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HubToolsScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SystemDiagnosticsScreen
import com.example.ui.theme.PkJarvisTheme
import com.example.ui.theme.StatusCrimson
import com.example.ui.viewmodel.JarvisViewModel

private enum class JarvisDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_jarvis, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    API_VAULT("api_vault", R.string.nav_api_vault, Icons.Filled.Key, Icons.Outlined.Key),
    HUB("hub", R.string.nav_tools, Icons.Filled.Apps, Icons.Outlined.Apps),
    MEMORY("memory", R.string.nav_memory, Icons.Filled.Psychology, Icons.Outlined.Psychology),
    SYSTEM("system", R.string.nav_system, Icons.Filled.Tune, Icons.Outlined.Tune)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: JarvisViewModel = viewModel()
            PkJarvisRootApp(viewModel = viewModel)
        }
    }
}

@Composable
fun PkJarvisRootApp(viewModel: JarvisViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var currentDestination by rememberSaveable { mutableStateOf(JarvisDestination.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bannerMessage) {
        val msg = uiState.bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearBanner()
        }
    }

    // Proper BackHandler returning to primary JARVIS Dashboard from secondary tabs
    BackHandler(enabled = currentDestination != JarvisDestination.DASHBOARD) {
        currentDestination = JarvisDestination.DASHBOARD
    }

    PkJarvisTheme(darkTheme = uiState.activeProfile.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = if (uiState.bannerIsError) {
                                StatusCrimson
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (uiState.bannerIsError) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            JarvisDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            JarvisDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentModifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)

                        when (currentDestination) {
                            JarvisDestination.DASHBOARD -> DashboardScreen(
                                state = uiState,
                                viewModel = viewModel,
                                onNavigateToApiVault = { currentDestination = JarvisDestination.API_VAULT },
                                onNavigateToSystem = { currentDestination = JarvisDestination.SYSTEM },
                                modifier = contentModifier
                            )
                            JarvisDestination.API_VAULT -> ApiVaultScreen(
                                state = uiState,
                                viewModel = viewModel,
                                modifier = contentModifier
                            )
                            JarvisDestination.HUB -> HubToolsScreen(
                                state = uiState,
                                viewModel = viewModel,
                                onNavigateToJarvis = { currentDestination = JarvisDestination.DASHBOARD },
                                modifier = contentModifier
                            )
                            JarvisDestination.MEMORY -> MemoryScreen(
                                state = uiState,
                                viewModel = viewModel,
                                modifier = contentModifier
                            )
                            JarvisDestination.SYSTEM -> SystemDiagnosticsScreen(
                                state = uiState,
                                viewModel = viewModel,
                                modifier = contentModifier
                            )
                        }
                    }
                }
            }
        }
    }
}
