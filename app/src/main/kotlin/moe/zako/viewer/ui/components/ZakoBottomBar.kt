package moe.zako.viewer.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import moe.zako.viewer.ui.navigation.Screen

@Composable
fun ZakoBottomBar(navController: NavController) {
    val items = listOf(
        Screen.Home,
        Screen.Favorites,
        Screen.Profile
    )
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    NavigationBar {
        items.forEach { screen ->
            val isSelected = currentRoute == screen.route ||
                    (screen == Screen.Home && currentRoute?.startsWith("home") == true)
            NavigationBarItem(
                icon = {
                    screen.icon?.let {
                        Icon(imageVector = it, contentDescription = screen.title)
                    }
                },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        val destination = if (screen == Screen.Home) Screen.Home.createRoute() else screen.route
                        navController.navigate(destination) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NeoBottomBar(navController: NavController) = ZakoBottomBar(navController)
