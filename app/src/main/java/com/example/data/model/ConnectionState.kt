package com.example.data.model

enum class ConnectionStatus {
    DISCONNECTED,
    PREPARING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    FAILED
}

data class ConnectionState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeProfile: VlessProfile? = null,
    val connectedDurationSeconds: Long = 0,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadSpeedBps: Long = 0,
    val downloadSpeedBps: Long = 0,
    val pingMs: Long? = null,
    val vpnIp: String? = null,
    val errorMessage: String? = null,
    val lastConnectedTime: Long? = null
) {
    val isConnected: Boolean get() = status == ConnectionStatus.CONNECTED
    val isBusy: Boolean get() = status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.PREPARING ||
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.DISCONNECTING
}
