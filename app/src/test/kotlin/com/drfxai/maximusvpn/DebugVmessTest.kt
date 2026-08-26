package com.drfxai.maximusvpn

import com.drfxai.maximusvpn.vless.VlessParser
import org.junit.Test

class DebugVmessTest {

    @Test
    fun debug_vmess() {
        val uri = "vmess://eyJ2IjoiMiIsInBzIjoiVk1lc3MgTm9kZSIsImFkZCI6InVzLmV4YW1wbGUuY29tIiwicG9ydCI6IjQ0MyIsImlkIjoiZDQ2YjRlYjItNmFiYy00YjM3LTg2Y2QtZDk3MmUxYzQ1Njc4IiwiYWlkIjoiMCIsInNjeSI6ImF1dG8iLCJuZXQiOiJ0Y3AiLCJ0bHMiOiJ0bHMiLCJzbmkiOiJnb29nbGUuY29tIiwiaG9zdCI6IiIsInBhdGgiOiIvIiwiYWxwbiI6IiJ9"
        val result = VlessParser.parse(uri)
        println("Result: $result")
        if (result is com.drfxai.maximusvpn.core.AppResult.Error) {
            println("Error: ${result.error}")
        }
    }
}
