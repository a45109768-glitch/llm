package com.pocketai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketai.ui.chat.ChatScreen
import com.pocketai.ui.chat.ChatViewModel
import com.pocketai.ui.cloud.CloudProvidersScreen
import com.pocketai.ui.memory.MemoryScreen
import com.pocketai.ui.models.LocalModelsScreen
import com.pocketai.ui.obsidian.ObsidianScreen
import com.pocketai.ui.robot.RobotScreen
import com.pocketai.ui.settings.SettingsScreen
import com.pocketai.ui.settings.SettingsViewModel

@Composable
fun PocketAINavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route
    ) {
        composable(Screen.Chat.route) {
            val chatViewModel: ChatViewModel = viewModel()
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                onNavigateToCloudProviders = { navController.navigate(Screen.CloudProviders.route) },
                onNavigateToObsidian = { navController.navigate(Screen.Obsidian.route) }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLocalModels = { navController.navigate(Screen.LocalModels.route) },
                onNavigateToCloudProviders = { navController.navigate(Screen.CloudProviders.route) },
                onNavigateToMemory = { navController.navigate(Screen.Memory.route) },
                onNavigateToObsidian = { navController.navigate(Screen.Obsidian.route) },
                onNavigateToRobot = { navController.navigate(Screen.Robot.route) }
            )
        }

        composable(Screen.LocalModels.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            LocalModelsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CloudProviders.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            CloudProvidersScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Memory.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            MemoryScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Obsidian.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            ObsidianScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Robot.route) {
            val settingsViewModel: SettingsViewModel = viewModel()
            RobotScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
