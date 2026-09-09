package com.pocketai.robot

import kotlinx.coroutines.flow.Flow

interface RobotConnection {
    val connectionState: Flow<RobotConnectionState>
    suspend fun connect(serverUrl: String, authToken: String): Result<Unit>
    suspend fun disconnect()
    suspend fun sendCommand(command: RobotCommand): Result<Unit>
}
