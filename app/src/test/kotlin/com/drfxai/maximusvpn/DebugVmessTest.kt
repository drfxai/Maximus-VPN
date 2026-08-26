package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.vless.VlessParser
import com.drfxai.maximusvpn.core.AppResult
import org.junit.Test

class DebugVmessTest {

    @Test
    fun debug_vmess() {
        val uri = "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3MgTm9kZSIsImFkZCI6InVzLmV4YW1wbGUuY29tIiwicG9ydCI6IjQ0MyIsImlkIjoiZDQ2YjRlYjItNmFiYy00YjM3LTg2Y2QtZDk3MmUxYzQ1Njc4IiwiYWlkIjoiMCIsInNjeSI6ImF1dG8iLCJuZXQiOiJ0Y3AiLCJ0bHMiOiJ0bHMiLCJzbmkiOiJnb29nbGUuY29tIiwiaG9zdCI6IiIsInBhdGgiOiIvIiwiYWxwbiI6IiJ9"
        val result = VlessParser.parse(uri)
        println("Result type: ${result.javaClass.simpleName}")
        if (result is AppResult.Error) {
            println("Error: ${result.exception}")
            println("Message: ${result.userFriendlyMessage}")
        } else if (result is AppResult.Success) {
            val p = result.data
            println("Name: ${p.name}")
            println("Protocol: ${p.protocol}")
            println("Address: ${p.address}")
            println("Port: ${p.port}")
            println("UUID: ${p.uuid}")
            println("AlterId: ${p.alterId}")
            println("Encryption: ${p.encryption}")
            println("Transport: ${p.transport}")
            println("Security: ${p.security}")
            println("SNI: ${p.sni}")
        }
    }
}
