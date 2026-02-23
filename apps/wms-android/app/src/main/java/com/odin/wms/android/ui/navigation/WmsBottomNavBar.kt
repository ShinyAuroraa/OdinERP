package com.odin.wms.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

private data class BottomNavItem(
    val screen: WmsScreen,
    val label: String,
    val icon: ImageVector
)

private val navItems = listOf(
    BottomNavItem(WmsScreen.Dashboard,  "Dashboard",  Icons.Default.Dashboard),
    BottomNavItem(WmsScreen.Operations, "Operações",  Icons.Default.ListAlt),
    BottomNavItem(WmsScreen.Scanner,    "Scanner",    Icons.Default.QrCodeScanner),
    BottomNavItem(WmsScreen.Profile,    "Perfil",     Icons.Default.Person)
)

@Composable
fun WmsBottomNavBar(
    navController: NavController,
    currentRoute: String?
) {
    NavigationBar {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.screen.route,
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            popUpTo(WmsScreen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
