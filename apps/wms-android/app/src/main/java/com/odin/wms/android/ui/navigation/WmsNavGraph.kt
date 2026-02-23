package com.odin.wms.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.odin.wms.android.ui.dashboard.DashboardScreen
import com.odin.wms.android.ui.login.LoginScreen
import com.odin.wms.android.ui.operations.OperationsScreen
import com.odin.wms.android.ui.profile.ProfileScreen
import com.odin.wms.android.ui.scanner.BarcodeScannerScreen

sealed class WmsScreen(val route: String) {
    data object Login      : WmsScreen("login")
    data object Dashboard  : WmsScreen("dashboard")
    data object Operations : WmsScreen("operations")
    data object Scanner    : WmsScreen("scanner")
    data object Profile    : WmsScreen("profile")
}

@Composable
fun WmsNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != WmsScreen.Login.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                WmsBottomNavBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = WmsScreen.Login.route
            ) {
                composable(WmsScreen.Login.route) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(WmsScreen.Dashboard.route) {
                                popUpTo(WmsScreen.Login.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(WmsScreen.Dashboard.route) {
                    DashboardScreen(
                        onNavigateToOperations = {
                            navController.navigate(WmsScreen.Operations.route)
                        }
                    )
                }

                composable(WmsScreen.Operations.route) {
                    OperationsScreen(
                        onOperationClick = { /* navigate to specific operation in 8.2-8.4 */ }
                    )
                }

                composable(WmsScreen.Scanner.route) {
                    BarcodeScannerScreen(
                        onCodeDetected = { code, _ ->
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("scannedCode", code)
                            navController.popBackStack()
                        },
                        onClose = { navController.popBackStack() }
                    )
                }

                composable(WmsScreen.Profile.route) {
                    ProfileScreen(
                        onLogout = {
                            navController.navigate(WmsScreen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
