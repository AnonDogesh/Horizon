package com.horizonweb.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.horizonweb.ui.screens.BrowserScreen
import com.horizonweb.ui.screens.BrowserViewModel
import com.horizonweb.ui.screens.DownloadsScreen
import com.horizonweb.ui.screens.ExtensionsScreen
import com.horizonweb.ui.screens.TabsScreen
import com.horizonweb.ui.screens.PrivacySettingsScreen
import com.horizonweb.ui.screens.VaultFileViewerScreen
import com.horizonweb.ui.screens.VaultGalleryScreen
import com.horizonweb.ui.screens.VaultUnlockScreen

private const val ROOT_ROUTE = "root"
private const val BROWSER_ROUTE = "browser"
private const val TABS_ROUTE = "tabs"
private const val DOWNLOADS_ROUTE = "downloads"
private const val EXTENSIONS_ROUTE = "extensions"
private const val PRIVACY_ROUTE = "privacy"
private const val VAULT_UNLOCK_ROUTE = "vault_unlock"
private const val VAULT_GALLERY_ROUTE = "vault_gallery"
private const val VAULT_VIEWER_ROUTE = "vault_viewer/{fileId}"

@Composable
fun HorizonWebNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROOT_ROUTE
    ) {
        browserGraph(navController)
    }
}

private fun NavGraphBuilder.browserGraph(navController: NavHostController) {
    navigation(startDestination = BROWSER_ROUTE, route = ROOT_ROUTE) {
        composable(BROWSER_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            BrowserScreen(
                viewModel = sharedViewModel,
                onOpenTabs = { navController.navigate(TABS_ROUTE) },
                onOpenDownloads = { navController.navigate(DOWNLOADS_ROUTE) },
                onOpenVault = { navController.navigate(VAULT_UNLOCK_ROUTE) },
                onOpenExtensions = { navController.navigate(EXTENSIONS_ROUTE) },
                onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) }
            )
        }

        composable(TABS_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            TabsScreen(
                viewModel = sharedViewModel,
                onBackToBrowser = { navController.popBackStack() }
            )
        }

        composable(DOWNLOADS_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            DownloadsScreen(
                viewModel = sharedViewModel,
                onBackToBrowser = { navController.popBackStack() }
            )
        }



        composable(PRIVACY_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            PrivacySettingsScreen(
                viewModel = sharedViewModel,
                onBackToBrowser = { navController.popBackStack() }
            )
        }

        composable(EXTENSIONS_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            ExtensionsScreen(
                viewModel = sharedViewModel,
                onBackToBrowser = { navController.popBackStack() }
            )
        }

        composable(VAULT_UNLOCK_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            val isLocked by sharedViewModel.isVaultLocked.collectAsStateWithLifecycle()
            if (!isLocked) {
                navController.navigate(VAULT_GALLERY_ROUTE) {
                    popUpTo(VAULT_UNLOCK_ROUTE) { inclusive = true }
                }
            } else {
                VaultUnlockScreen(
                    viewModel = sharedViewModel,
                    onUnlocked = {
                        navController.navigate(VAULT_GALLERY_ROUTE) {
                            popUpTo(VAULT_UNLOCK_ROUTE) { inclusive = true }
                        }
                    },
                    onBackToBrowser = { navController.popBackStack() }
                )
            }
        }

        composable(VAULT_GALLERY_ROUTE) {
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            val isLocked by sharedViewModel.isVaultLocked.collectAsStateWithLifecycle()
            if (isLocked) {
                navController.navigate(VAULT_UNLOCK_ROUTE) {
                    popUpTo(VAULT_GALLERY_ROUTE) { inclusive = true }
                }
            } else {
                VaultGalleryScreen(
                    viewModel = sharedViewModel,
                    onOpenFile = { id -> navController.navigate("vault_viewer/$id") },
                    onBackToBrowser = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = VAULT_VIEWER_ROUTE,
            arguments = listOf(navArgument("fileId") { type = NavType.LongType })
        ) { backStackEntry ->
            val parentEntry = remember { navController.getBackStackEntry(ROOT_ROUTE) }
            val sharedViewModel: BrowserViewModel = hiltViewModel(parentEntry)
            val isLocked by sharedViewModel.isVaultLocked.collectAsStateWithLifecycle()
            if (isLocked) {
                navController.navigate(VAULT_UNLOCK_ROUTE) {
                    popUpTo(ROOT_ROUTE)
                }
            } else {
                val fileId = backStackEntry.arguments?.getLong("fileId") ?: -1L
                VaultFileViewerScreen(
                    viewModel = sharedViewModel,
                    fileId = fileId,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}
