package com.example.agyremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigTest {

    @Test
    fun testDefaultConfigUrls() {
        val config = ConnectionConfig(hostIp = "192.168.1.100", port = 4400)
        assertEquals("http://192.168.1.100:4400", config.httpUrl)
        assertEquals("ws://192.168.1.100:4400/connect-websocket", config.wsUrl)
        assertTrue(config.autoReconnect)
        assertTrue(config.notificationsEnabled)
    }

    @Test
    fun testCustomPortAndHost() {
        val config = ConnectionConfig(hostIp = "10.0.0.5", port = 8080)
        assertEquals("http://10.0.0.5:8080", config.httpUrl)
        assertEquals("ws://10.0.0.5:8080/connect-websocket", config.wsUrl)
    }
}
