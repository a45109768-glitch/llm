package com.pocketai.robot

enum class RobotStatus(val displayName: String) {
    STANDBY("Standby"),
    CONNECTED("Connected"),
    OFFLINE("Offline"),
    ERROR("Error")
}

data class RobotDevice(
    val id: String = "esp32_bot_01",
    val name: String = "PocketBot Alpha",
    val hardwarePlatform: String = "ESP32-S3 Microcontroller",
    val serverGatewayUrl: String = "https://server.pocketai.internal",
    val authToken: String = "",
    val pairedToken: String = "",
    val status: RobotStatus = RobotStatus.STANDBY,
    val connectionState: RobotConnectionState = RobotConnectionState.STANDBY,
    val batteryPercent: Int? = null,
    val firmwareVersion: String = "v0.1.0-alpha",
    val lastPingTimestamp: Long? = null
)

enum class RobotConnectionState {
    DISCONNECTED,
    CONNECTING,
    STANDBY,
    ACTIVE_CONTROLLER,
    ERROR
}

data class RobotCommand(
    val commandId: String = java.util.UUID.randomUUID().toString(),
    val action: String,
    val payloadJson: String = "{}"
)
