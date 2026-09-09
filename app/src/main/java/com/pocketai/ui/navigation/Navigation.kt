package com.pocketai.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Chat : Screen("chat", "PocketAI")
    object Settings : Screen("settings", "Settings")
    object LocalModels : Screen("local_models", "Local GGUF Models")
    object CloudProviders : Screen("cloud_providers", "Cloud AI Providers")
    object Memory : Screen("memory", "Personal Memory")
    object Obsidian : Screen("obsidian", "Obsidian Vault")
    object Robot : Screen("robot", "Robot Control")
}
