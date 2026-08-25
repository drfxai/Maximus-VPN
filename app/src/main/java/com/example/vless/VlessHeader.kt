package com.example.vless

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

object VlessHeader {

    const val COMMAND_TCP: Byte = 0x01
    const val COMMAND_UDP: Byte = 0x02
    const val COMMAND_MUX: Byte = 0x03

    const val ATYPE_IPV4: Byte = 0x01
    const val ATYPE_DOMAIN: Byte = 0x02
    const val ATYPE_IPV6: Byte = 0x03

    /**
     * Converts a UUID string (e.g. 12345678-1234-1234-1234-123456789abc) into 16 bytes.
     */
    fun uuidToBytes(uuidStr: String): ByteArray {
        val clean = uuidStr.replace("-", "").trim()
        if (clean.length != 32) {
            // Fallback padding/truncation
            val result = ByteArray(16)
            return result
        }
        val bytes = ByteArray(16)
        for (i in 0 until 16) {
            val hex = clean.substring(i * 2, i * 2 + 2)
            bytes[i] = hex.toInt(16).toByte()
        }
        return bytes
    }

    /**
     * Encodes a VLESS request header for a target destination (IPv4, IPv6, or Domain).
     */
    fun encodeRequest(
        uuidBytes: ByteArray,
        command: Byte,
        destPort: Int,
        destAddress: String
    ): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Version: 0x00
        out.write(0x00)

        // 2. User ID: 16 bytes
        if (uuidBytes.size == 16) {
            out.write(uuidBytes)
        } else {
            out.write(ByteArray(16))
        }

        // 3. Proto Addons Length: 0x00
        out.write(0x00)

        // 4. Command: 0x01 (TCP) or 0x02 (UDP)
        out.write(command.toInt())

        // 5. Port: 2 bytes (Big Endian)
        out.write((destPort shr 8) and 0xFF)
        out.write(destPort and 0xFF)

        // 6. Address Type and Address bytes
        if (isIpv4(destAddress)) {
            out.write(ATYPE_IPV4.toInt())
            val parts = destAddress.split('.')
            for (p in parts) {
                out.write(p.toIntOrNull() ?: 0)
            }
        } else if (isIpv6(destAddress)) {
            out.write(ATYPE_IPV6.toInt())
            try {
                val ipBytes = java.net.InetAddress.getByName(destAddress).address
                if (ipBytes.size == 16) {
                    out.write(ipBytes)
                } else {
                    out.write(ByteArray(16))
                }
            } catch (_: Exception) {
                out.write(ByteArray(16))
            }
        } else {
            // Domain name
            out.write(ATYPE_DOMAIN.toInt())
            val domainBytes = destAddress.toByteArray(Charsets.UTF_8)
            out.write(domainBytes.size and 0xFF)
            out.write(domainBytes)
        }

        return out.toByteArray()
    }

    /**
     * Decodes the VLESS server response header (Version + Addons).
     */
    fun decodeResponse(inputStream: InputStream): Boolean {
        try {
            val version = inputStream.read()
            if (version == -1) return false
            val addonLen = inputStream.read()
            if (addonLen == -1) return false
            if (addonLen > 0) {
                val skipped = inputStream.skip(addonLen.toLong())
                if (skipped < addonLen) return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun isIpv4(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }

    private fun isIpv6(address: String): Boolean {
        return address.contains(':')
    }
}
