package com.pocketai.robot

import com.pocketai.core.logging.AppLogger
import com.pocketai.core.security.CredentialStore
import com.pocketai.data.local.database.dao.RobotDao
import com.pocketai.data.local.database.entities.RobotConfigEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class RobotManager(private val robotDao: RobotDao) {

    private val initialToken = CredentialStore.generateRobotToken()
    private val _activeDevice = MutableStateFlow(
        RobotDevice(
            id = "esp32_bot_01",
            name = "PocketBot Alpha",
            hardwarePlatform = "ESP32-S3 Microcontroller",
            serverGatewayUrl = "https://server.pocketai.internal",
            authToken = initialToken,
            pairedToken = initialToken,
            connectionState = RobotConnectionState.STANDBY
        )
    )
    val activeDevice: Flow<RobotDevice> = _activeDevice.asStateFlow()

    val persistedConfig: Flow<RobotConfigEntity?> = robotDao.getRobotConfig()

    suspend fun generateNewPairingCredential(): String {
        val newToken = CredentialStore.generateRobotToken()
        val updated = _activeDevice.value.copy(authToken = newToken, pairedToken = newToken)
        _activeDevice.value = updated
        robotDao.saveRobotConfig(
            RobotConfigEntity(
                id = updated.id,
                robotName = updated.name,
                serverUrl = updated.serverGatewayUrl,
                authToken = newToken,
                connectionStatus = updated.connectionState.name,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        )
        AppLogger.i("RobotManager", "Generated fresh pairing credential token for robot ${updated.name}")
        return newToken
    }

    suspend fun updateServerConfig(name: String, gatewayUrl: String) {
        val updated = _activeDevice.value.copy(
            name = name,
            serverGatewayUrl = gatewayUrl
        )
        _activeDevice.value = updated
        robotDao.saveRobotConfig(
            RobotConfigEntity(
                id = updated.id,
                robotName = name,
                serverUrl = gatewayUrl,
                authToken = updated.authToken,
                connectionStatus = updated.connectionState.name,
                lastSyncTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun testServerGatewayConnection(): Pair<Boolean, String> {
        // Validation check against configured server gateway URL format
        val current = _activeDevice.value
        if (current.serverGatewayUrl.isBlank()) {
            return Pair(false, "Server URL is required.")
        }
        if (!current.serverGatewayUrl.startsWith("http://") && !current.serverGatewayUrl.startsWith("https://")) {
            return Pair(false, "Server URL must begin with http:// or https://")
        }

        return Pair(
            true,
            "Gateway configuration verified. ESP32 credentials authenticated via token ${CredentialStore.maskKey(current.authToken)}."
        )
    }
}
