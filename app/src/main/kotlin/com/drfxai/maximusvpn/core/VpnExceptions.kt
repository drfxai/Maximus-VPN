package com.drfxai.maximusvpn.core

sealed class VpnException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidVlessUrl(message: String) : VpnException("Invalid VLESS URL: $message")
    class InvalidUuid(val uuid: String) : VpnException("Invalid UUID format: '$uuid'. Must be a 36-character canonical UUID.")
    class InvalidPort(val port: Int) : VpnException("Invalid port number: $port. Must be between 1 and 65535.")
    class MissingHost : VpnException("Server address/hostname cannot be empty.")
    class MissingUuid : VpnException("User ID (UUID) is required for VLESS authentication.")
    class UnsupportedTransport(val transport: String) : VpnException("Unsupported transport type: $transport.")
    class XrayStartupFailed(message: String, cause: Throwable? = null) : VpnException("Xray-core startup failed: $message", cause)
    class VpnPermissionDenied : VpnException("Android VPN permission was denied by the user.")
    class NetworkUnavailable : VpnException("No active internet connection available.")
    class ConnectionTimeout(message: String = "Connection to remote server timed out.") : VpnException(message)
    class ConfigurationError(message: String) : VpnException("Configuration error: $message")
}

sealed class AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable, val userFriendlyMessage: String) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw exception
    }
}
