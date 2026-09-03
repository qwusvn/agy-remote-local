package com.example.agyremote.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LanScannerTest {

    @Test
    fun testDiscoveredHostProperties() {
        val host = DiscoveredHost(ip = "192.168.1.50", port = 4400, responseTimeMs = 12)
        assertEquals("192.168.1.50", host.ip)
        assertEquals(4400, host.port)
        assertEquals(12L, host.responseTimeMs)
    }

    @Test
    fun testSubnetExtractionLogic() {
        val testIp = "192.168.1.15"
        val lastDotIndex = testIp.lastIndexOf('.')
        val subnet = testIp.substring(0, lastDotIndex + 1)
        assertEquals("192.168.1.", subnet)
    }
}
