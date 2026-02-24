package com.odin.wms.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.odin.wms.android.ui.dashboard.DashboardScreen
import com.odin.wms.android.ui.login.LoginScreen
import com.odin.wms.android.ui.operations.OperationsScreen
import com.odin.wms.android.ui.profile.ProfileScreen
import com.odin.wms.android.ui.receiving.DivergenceScreen
import com.odin.wms.android.ui.receiving.ReceivingConfirmScreen
import com.odin.wms.android.ui.receiving.ReceivingDetailScreen
import com.odin.wms.android.ui.receiving.ReceivingListScreen
import com.odin.wms.android.ui.receiving.SignatureScreen
import com.odin.wms.android.ui.scanner.BarcodeScannerScreen

sealed class WmsScreen(val route: String) {
    data object Login           : WmsScreen("login")
    data object Dashboard       : WmsScreen("dashboard")
    data object Operations      : WmsScreen("operations")
    data object Scanner         : WmsScreen("scanner")
    data object Profile         : WmsScreen("profile")
    data object ReceivingList   : WmsScreen("receiving_list")
    data object ReceivingDetail : WmsScreen("receiving_detail/{orderId}") {
        fun route(orderId: String) = "receiving_detail/$orderId"
    }
    data object ReceivingConfirm : WmsScreen("receiving_confirm/{orderId}/{itemId}") {
        fun route(
            orderId: String,
            itemId: String,
            scannedCode: String? = null,
            expectedQty: Int? = null
        ): String {
            var r = "receiving_confirm/$orderId/$itemId"
            val params = buildList {
                if (scannedCode != null) add("scannedCode=$scannedCode")
                if (expectedQty != null) add("expectedQty=$expectedQty")
            }
            if (params.isNotEmpty()) r += "?" + params.joinToString("&")
            return r
        }
    }
    data object Divergence : WmsScreen("divergence/{orderId}/{itemId}") {
        fun route(orderId: String, itemId: String) = "divergence/$orderId/$itemId"
    }
    data object Signature : WmsScreen("signature/{orderId}") {
        fun route(orderId: String) = "signature/$orderId"
    }
}

@Composable
fun WmsNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on Login and entire receiving flow
    val showBottomBar = currentRoute != null &&
        currentRoute != WmsScreen.Login.route &&
        !currentRoute.startsWith("receiving_detail") &&
        !currentRoute.startsWith("receiving_confirm") &&
        !currentRoute.startsWith("divergence/") &&
        !currentRoute.startsWith("signature/")

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
                        onOperationClick = { operation ->
                            when (operation) {
                                "Recebimento" -> navController.navigate(WmsScreen.ReceivingList.route)
                                else -> { /* Story 8.3: Picking, 8.4: Inventory/Transfer */ }
                            }
                        }
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

                // ----- Receiving Flow -----

                composable(WmsScreen.ReceivingList.route) {
                    ReceivingListScreen(
                        onOrderClick = { orderId ->
                            navController.navigate(WmsScreen.ReceivingDetail.route(orderId))
                        }
                    )
                }

                composable(
                    route = WmsScreen.ReceivingDetail.route,
                    arguments = listOf(
                        navArgument("orderId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
                    val scannedCode = backStackEntry.savedStateHandle.get<String>("scannedCode")

                    ReceivingDetailScreen(
                        orderId = orderId,
                        onNavigateBack = { navController.popBackStack() },
                        onScanClick = { _ ->
                            navController.navigate(WmsScreen.Scanner.route)
                        },
                        onConfirmItem = { _, itemId ->
                            val route = WmsScreen.ReceivingConfirm.route(
                                orderId = orderId,
                                itemId = itemId,
                                scannedCode = scannedCode
                            )
                            navController.navigate(route)
                        },
                        onDivergenceClick = { _, itemId ->
                            navController.navigate(WmsScreen.Divergence.route(orderId, itemId))
                        },
                        onFinalizeClick = { _ ->
                            navController.navigate(WmsScreen.Signature.route(orderId))
                        }
                    )
                }

                composable(
                    route = "${WmsScreen.ReceivingConfirm.route}?scannedCode={scannedCode}&expectedQty={expectedQty}",
                    arguments = listOf(
                        navArgument("orderId")     { type = NavType.StringType },
                        navArgument("itemId")      { type = NavType.StringType },
                        navArgument("scannedCode") { type = NavType.StringType; defaultValue = "" },
                        navArgument("expectedQty") { type = NavType.IntType; defaultValue = 1 }
                    )
                ) { backStackEntry ->
                    val orderId    = backStackEntry.arguments?.getString("orderId") ?: return@composable
                    val itemId     = backStackEntry.arguments?.getString("itemId") ?: return@composable
                    val scannedCode = backStackEntry.arguments?.getString("scannedCode")?.ifBlank { null }
                    val expectedQty = backStackEntry.arguments?.getInt("expectedQty") ?: 1

                    ReceivingConfirmScreen(
                        orderId = orderId,
                        itemId = itemId,
                        scannedCode = scannedCode,
                        expectedQty = expectedQty,
                        onNavigateBack = { navController.popBackStack() },
                        onConfirmSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.Divergence.route,
                    arguments = listOf(
                        navArgument("orderId") { type = NavType.StringType },
                        navArgument("itemId")  { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
                    val itemId  = backStackEntry.arguments?.getString("itemId") ?: return@composable

                    DivergenceScreen(
                        orderId = orderId,
                        itemId = itemId,
                        onNavigateBack = { navController.popBackStack() },
                        onDivergenceReported = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.Signature.route,
                    arguments = listOf(
                        navArgument("orderId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable

                    SignatureScreen(
                        orderId = orderId,
                        onNavigateBack = { navController.popBackStack() },
                        onSignatureComplete = {
                            navController.navigate(WmsScreen.ReceivingList.route) {
                                popUpTo(WmsScreen.ReceivingList.route) { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
    }
}
