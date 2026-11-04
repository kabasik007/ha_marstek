package ua.zilla.energy.data

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarstekDiscoveryTest {
    private fun ip(address: String) = InetAddress.getByName(address) as Inet4Address

    @Test fun calculatesDirectedBroadcastForHomeWifi() {
        assertEquals(
            "192.168.0.255",
            MarstekDiscovery.broadcastAddress(ip("192.168.0.61"), 24)?.hostAddress
        )
        assertEquals(
            "10.20.255.255",
            MarstekDiscovery.broadcastAddress(ip("10.20.15.100"), 16)?.hostAddress
        )
    }

    @Test fun refusesPointToPointAndUnspecifiedPrefixes() {
        assertNull(MarstekDiscovery.broadcastAddress(ip("192.168.0.61"), 31))
        assertNull(MarstekDiscovery.broadcastAddress(ip("192.168.0.61"), 32))
        assertNull(MarstekDiscovery.broadcastAddress(ip("192.168.0.61"), 0))
    }
}
