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
import com.odin.wms.android.ui.inventory.InventoryCountScreen
import com.odin.wms.android.ui.inventory.InventoryCountingListScreen
import com.odin.wms.android.ui.inventory.InventoryDoubleCountScreen
import com.odin.wms.android.ui.inventory.InventorySessionListScreen
import com.odin.wms.android.ui.inventory.InventorySubmitScreen
import com.odin.wms.android.ui.login.LoginScreen
import com.odin.wms.android.ui.operations.OperationsScreen
import com.odin.wms.android.ui.picking.PickingCompleteScreen
import com.odin.wms.android.ui.picking.PickingConfirmScreen
import com.odin.wms.android.ui.picking.PickingDetailScreen
import com.odin.wms.android.ui.picking.PickingListScreen
import com.odin.wms.android.ui.profile.ProfileScreen
import com.odin.wms.android.ui.receiving.DivergenceScreen
import com.odin.wms.android.ui.receiving.ReceivingConfirmScreen
import com.odin.wms.android.ui.receiving.ReceivingDetailScreen
import com.odin.wms.android.ui.receiving.ReceivingListScreen
import com.odin.wms.android.ui.receiving.SignatureScreen
import com.odin.wms.android.ui.scanner.BarcodeScannerScreen
import com.odin.wms.android.ui.shipping.ShippingConfirmScreen
import com.odin.wms.android.ui.shipping.ShippingDetailScreen
import com.odin.wms.android.ui.shipping.ShippingListScreen
import com.odin.wms.android.ui.transfer.TransferConfirmScreen
import com.odin.wms.android.ui.transfer.TransferCreateScreen
import com.odin.wms.android.ui.transfer.TransferListScreen

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

    // --- Picking Flow (Story 8.3) ---
    data object PickingList   : WmsScreen("picking_list")
    data object PickingDetail : WmsScreen("picking_detail/{taskId}") {
        fun route(taskId: String) = "picking_detail/$taskId"
    }
    data object PickingConfirm : WmsScreen("picking_confirm/{taskId}/{itemId}") {
        fun route(
            taskId: String,
            itemId: String,
            scannedCode: String? = null,
            expectedQty: Int? = null
        ): String {
            var r = "picking_confirm/$taskId/$itemId"
            val params = buildList {
                if (scannedCode != null) add("scannedCode=$scannedCode")
                if (expectedQty != null) add("expectedQty=$expectedQty")
            }
            if (params.isNotEmpty()) r += "?" + params.joinToString("&")
            return r
        }
    }
    data object PickingComplete : WmsScreen("picking_complete/{taskId}") {
        fun route(taskId: String) = "picking_complete/$taskId"
    }

    // --- Shipping Flow (Story 8.3) ---
    data object ShippingList   : WmsScreen("shipping_list")
    data object ShippingDetail : WmsScreen("shipping_detail/{shipmentId}") {
        fun route(shipmentId: String) = "shipping_detail/$shipmentId"
    }
    data object ShippingConfirm : WmsScreen("shipping_confirm/{shipmentId}/{packageId}") {
        fun route(
            shipmentId: String,
            packageId: String,
            scannedCode: String? = null,
            vehiclePlate: String? = null
        ): String {
            var r = "shipping_confirm/$shipmentId/$packageId"
            val params = buildList {
                if (scannedCode != null) add("scannedCode=$scannedCode")
                if (vehiclePlate != null) add("vehiclePlate=$vehiclePlate")
            }
            if (params.isNotEmpty()) r += "?" + params.joinToString("&")
            return r
        }
    }

    // --- Inventory Flow (Story 8.4) ---
    data object InventorySessionList : WmsScreen("inventory_session_list")
    data object InventoryCountingList : WmsScreen("inventory_counting_list/{sessionId}") {
        fun route(sessionId: String) = "inventory_counting_list/$sessionId"
    }
    data object InventoryCount : WmsScreen("inventory_count/{sessionId}/{itemId}") {
        fun route(sessionId: String, itemId: String) = "inventory_count/$sessionId/$itemId"
    }
    data object InventoryDoubleCount : WmsScreen("inventory_double_count/{sessionId}/{itemId}") {
        fun route(sessionId: String, itemId: String) = "inventory_double_count/$sessionId/$itemId"
    }
    data object InventorySubmit : WmsScreen("inventory_submit/{sessionId}") {
        fun route(sessionId: String) = "inventory_submit/$sessionId"
    }

    // --- Transfer Flow (Story 8.4) ---
    data object TransferList   : WmsScreen("transfer_list")
    data object TransferCreate : WmsScreen("transfer_create")
}

@Composable
fun WmsNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom bar on Login and detail flows
    val showBottomBar = currentRoute != null &&
        currentRoute != WmsScreen.Login.route &&
        !currentRoute.startsWith("receiving_detail") &&
        !currentRoute.startsWith("receiving_confirm") &&
        !currentRoute.startsWith("divergence/") &&
        !currentRoute.startsWith("signature/") &&
        !currentRoute.startsWith("picking_detail") &&
        !currentRoute.startsWith("picking_confirm") &&
        !currentRoute.startsWith("picking_complete") &&
        !currentRoute.startsWith("shipping_detail") &&
        !currentRoute.startsWith("shipping_confirm") &&
        !currentRoute.startsWith("inventory_counting_list") &&
        !currentRoute.startsWith("inventory_count") &&
        !currentRoute.startsWith("inventory_double_count") &&
        !currentRoute.startsWith("inventory_submit") &&
        !currentRoute.startsWith("transfer_create")

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
                                "Recebimento"   -> navController.navigate(WmsScreen.ReceivingList.route)
                                "Picking"       -> navController.navigate(WmsScreen.PickingList.route)
                                "Expedição"     -> navController.navigate(WmsScreen.ShippingList.route)
                                "Inventário"    -> navController.navigate(WmsScreen.InventorySessionList.route)
                                "Transferências" -> navController.navigate(WmsScreen.TransferList.route)
                                else -> { /* Future operations */ }
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

                // ----- Picking Flow (Story 8.3) -----

                composable(WmsScreen.PickingList.route) {
                    PickingListScreen(
                        onTaskClick = { taskId ->
                            navController.navigate(WmsScreen.PickingDetail.route(taskId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.PickingDetail.route,
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
                    val scannedCode = backStackEntry.savedStateHandle.get<String>("scannedCode")

                    PickingDetailScreen(
                        taskId = taskId,
                        onScanClick = {
                            navController.navigate(WmsScreen.Scanner.route)
                        },
                        onConfirmItem = { _, itemId ->
                            val route = WmsScreen.PickingConfirm.route(
                                taskId = taskId,
                                itemId = itemId,
                                scannedCode = scannedCode
                            )
                            navController.navigate(route)
                        },
                        onCompleteTask = { _ ->
                            navController.navigate(WmsScreen.PickingComplete.route(taskId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${WmsScreen.PickingConfirm.route}?scannedCode={scannedCode}&expectedQty={expectedQty}",
                    arguments = listOf(
                        navArgument("taskId")      { type = NavType.StringType },
                        navArgument("itemId")      { type = NavType.StringType },
                        navArgument("scannedCode") { type = NavType.StringType; defaultValue = "" },
                        navArgument("expectedQty") { type = NavType.IntType; defaultValue = 1 }
                    )
                ) { backStackEntry ->
                    val taskId      = backStackEntry.arguments?.getString("taskId") ?: return@composable
                    val itemId      = backStackEntry.arguments?.getString("itemId") ?: return@composable
                    val scannedCode = backStackEntry.arguments?.getString("scannedCode")?.ifBlank { null }
                    val expectedQty = backStackEntry.arguments?.getInt("expectedQty") ?: 1

                    PickingConfirmScreen(
                        taskId = taskId,
                        itemId = itemId,
                        scannedCode = scannedCode,
                        expectedQty = expectedQty,
                        onNavigateBack = { navController.popBackStack() },
                        onConfirmSuccess = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.PickingComplete.route,
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable

                    PickingCompleteScreen(
                        taskId = taskId,
                        onBackToList = {
                            navController.navigate(WmsScreen.PickingList.route) {
                                popUpTo(WmsScreen.PickingList.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ----- Shipping Flow (Story 8.3) -----

                composable(WmsScreen.ShippingList.route) {
                    ShippingListScreen(
                        onOrderClick = { orderId ->
                            navController.navigate(WmsScreen.ShippingDetail.route(orderId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.ShippingDetail.route,
                    arguments = listOf(
                        navArgument("shipmentId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val shipmentId = backStackEntry.arguments?.getString("shipmentId") ?: return@composable
                    val scannedCode = backStackEntry.savedStateHandle.get<String>("scannedCode")

                    ShippingDetailScreen(
                        orderId = shipmentId,
                        onScanClick = {
                            navController.navigate(WmsScreen.Scanner.route)
                        },
                        onConfirmPackage = { _, packageId ->
                            val route = WmsScreen.ShippingConfirm.route(
                                shipmentId = shipmentId,
                                packageId = packageId,
                                scannedCode = scannedCode
                            )
                            navController.navigate(route)
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "${WmsScreen.ShippingConfirm.route}?scannedCode={scannedCode}&vehiclePlate={vehiclePlate}",
                    arguments = listOf(
                        navArgument("shipmentId")   { type = NavType.StringType },
                        navArgument("packageId")    { type = NavType.StringType },
                        navArgument("scannedCode")  { type = NavType.StringType; defaultValue = "" },
                        navArgument("vehiclePlate") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val shipmentId    = backStackEntry.arguments?.getString("shipmentId") ?: return@composable
                    val packageId     = backStackEntry.arguments?.getString("packageId") ?: return@composable
                    val scannedCode   = backStackEntry.arguments?.getString("scannedCode")?.ifBlank { null }
                    val vehiclePlate  = backStackEntry.arguments?.getString("vehiclePlate")?.ifBlank { null }

                    ShippingConfirmScreen(
                        orderId = shipmentId,
                        packageId = packageId,
                        scannedCode = scannedCode,
                        initialVehiclePlate = vehiclePlate,
                        onNavigateBack = { navController.popBackStack() },
                        onConfirmSuccess = { navController.popBackStack() }
                    )
                }

                // ----- Inventory Flow (Story 8.4) -----

                composable(WmsScreen.InventorySessionList.route) {
                    InventorySessionListScreen(
                        onSessionClick = { sessionId ->
                            navController.navigate(WmsScreen.InventoryCountingList.route(sessionId))
                        }
                    )
                }

                composable(
                    route = WmsScreen.InventoryCountingList.route,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val scannedCode = backStackEntry.savedStateHandle.get<String>("scannedCode")

                    InventoryCountingListScreen(
                        sessionId = sessionId,
                        onItemClick = { sId, itemId ->
                            navController.navigate(WmsScreen.InventoryCount.route(sId, itemId))
                        },
                        onScanClick = { navController.navigate(WmsScreen.Scanner.route) },
                        onSubmitClick = { sId ->
                            navController.navigate(WmsScreen.InventorySubmit.route(sId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.InventoryCount.route,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("itemId")    { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val itemId    = backStackEntry.arguments?.getString("itemId") ?: return@composable

                    InventoryCountScreen(
                        sessionId = sessionId,
                        itemId = itemId,
                        onCountSuccess = { navController.popBackStack() },
                        onDoubleCountClick = { sId, iId ->
                            navController.navigate(WmsScreen.InventoryDoubleCount.route(sId, iId))
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.InventoryDoubleCount.route,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType },
                        navArgument("itemId")    { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    val itemId    = backStackEntry.arguments?.getString("itemId") ?: return@composable

                    InventoryDoubleCountScreen(
                        sessionId = sessionId,
                        itemId = itemId,
                        onDoubleCountComplete = { navController.popBackStack() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = WmsScreen.InventorySubmit.route,
                    arguments = listOf(
                        navArgument("sessionId") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable

                    InventorySubmitScreen(
                        sessionId = sessionId,
                        onSubmitSuccess = {
                            navController.navigate(WmsScreen.InventorySessionList.route) {
                                popUpTo(WmsScreen.InventorySessionList.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // ----- Transfer Flow (Story 8.4) -----

                composable(WmsScreen.TransferList.route) {
                    TransferListScreen(
                        onCreateClick = { navController.navigate(WmsScreen.TransferCreate.route) },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable(WmsScreen.TransferCreate.route) { backStackEntry ->
                    val scannedCode = backStackEntry.savedStateHandle.get<String>("scannedCode")

                    TransferCreateScreen(
                        scannedCode = scannedCode,
                        onScanClick = { navController.navigate(WmsScreen.Scanner.route) },
                        onConfirmClick = { sourceLocation, productCode, qty, lotNumber, destinationLocation ->
                            // Store in backstack for TransferConfirmScreen via savedStateHandle
                            navController.currentBackStackEntry?.savedStateHandle?.let { handle ->
                                handle["transfer_source"] = sourceLocation
                                handle["transfer_product"] = productCode
                                handle["transfer_qty"] = qty
                                handle["transfer_lot"] = lotNumber ?: ""
                                handle["transfer_dest"] = destinationLocation
                            }
                            navController.navigate("transfer_confirm_screen")
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("transfer_confirm_screen") {
                    val prevEntry = navController.previousBackStackEntry
                    val sourceLocation = prevEntry?.savedStateHandle?.get<String>("transfer_source") ?: ""
                    val productCode    = prevEntry?.savedStateHandle?.get<String>("transfer_product") ?: ""
                    val qty            = prevEntry?.savedStateHandle?.get<Int>("transfer_qty") ?: 0
                    val lotText        = prevEntry?.savedStateHandle?.get<String>("transfer_lot") ?: ""
                    val destLocation   = prevEntry?.savedStateHandle?.get<String>("transfer_dest") ?: ""

                    TransferConfirmScreen(
                        sourceLocation = sourceLocation,
                        productCode = productCode,
                        qty = qty,
                        lotNumber = lotText.ifBlank { null },
                        destinationLocation = destLocation,
                        onConfirmSuccess = {
                            navController.navigate(WmsScreen.TransferList.route) {
                                popUpTo(WmsScreen.TransferList.route) { inclusive = true }
                            }
                        },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
