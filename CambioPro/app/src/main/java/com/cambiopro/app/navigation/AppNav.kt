package com.cambiopro.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cambiopro.app.di.AppContainer
import com.cambiopro.app.ui.ConverterViewModel
import com.cambiopro.app.ui.DetailViewModel
import com.cambiopro.app.ui.FavoritesViewModel
import com.cambiopro.app.ui.HistoryViewModel
import com.cambiopro.app.ui.MarketViewModel
import com.cambiopro.app.ui.SettingsViewModel
import com.cambiopro.app.ui.VmFactory
import com.cambiopro.app.ui.screens.AboutScreen
import com.cambiopro.app.ui.screens.ConverterScreen
import com.cambiopro.app.ui.screens.CryptoScreen
import com.cambiopro.app.ui.screens.DetailScreen
import com.cambiopro.app.ui.screens.FavoritesScreen
import com.cambiopro.app.ui.screens.HistoryScreen
import com.cambiopro.app.ui.screens.HomeScreen
import com.cambiopro.app.ui.screens.SettingsScreen

private data class TopDest(val route: String, val label: String, val icon: ImageVector)

private val TOP = listOf(
    TopDest("home", "Início", Icons.Filled.Home),
    TopDest("crypto", "Cripto", Icons.Filled.CurrencyBitcoin),
    TopDest("converter", "Conversor", Icons.Filled.CurrencyExchange),
    TopDest("favorites", "Favoritos", Icons.Filled.Star),
    TopDest("settings", "Ajustes", Icons.Filled.Settings),
)

@Composable
fun AppRoot(container: AppContainer) {
    val nav = rememberNavController()
    val factory = remember { VmFactory(container) }
    // ViewModels no escopo da Activity: o estado sobrevive à troca de abas e à rotação da tela.
    val owner = checkNotNull(LocalViewModelStoreOwner.current)
    val market: MarketViewModel = viewModel(owner, factory = factory)
    val converter: ConverterViewModel = viewModel(owner, factory = factory)
    val favorites: FavoritesViewModel = viewModel(owner, factory = factory)
    val history: HistoryViewModel = viewModel(owner, factory = factory)
    val settings: SettingsViewModel = viewModel(owner, factory = factory)

    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val showBar = dest?.hierarchy?.any { d -> TOP.any { it.route == d.route } } == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TOP.forEach { t ->
                        NavigationBarItem(
                            selected = dest?.hierarchy?.any { it.route == t.route } == true,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner).then(if (!showBar) Modifier.navigationBarsPadding() else Modifier)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(market) { id -> nav.navigate("detail/$id") } }
                composable("crypto") { CryptoScreen(market) { id -> nav.navigate("detail/$id") } }
                composable("converter") { ConverterScreen(converter) { nav.navigate("history") } }
                composable("favorites") { FavoritesScreen(favorites) { id -> nav.navigate("detail/$id") } }
                composable("settings") {
                    SettingsScreen(settings, onOpenHistory = { nav.navigate("history") }, onOpenAbout = { nav.navigate("about") })
                }
                composable(
                    "detail/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { e ->
                    val id = e.arguments?.getString("id").orEmpty()
                    val vm: DetailViewModel = viewModel(
                        key = "detail_$id",
                        factory = viewModelFactory { initializer { DetailViewModel(container, id) } },
                    )
                    DetailScreen(vm, id) { nav.popBackStack() }
                }
                composable("history") { HistoryScreen(history) { nav.popBackStack() } }
                composable("about") { AboutScreen { nav.popBackStack() } }
            }
        }
    }
}
