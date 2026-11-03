package ua.zilla.energy.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressTest {
    @Test fun acceptsPrivateLanOnly() {
        assertTrue(LocalAddress.valid("192.168.0.61"))
        assertTrue(LocalAddress.valid("10.0.0.5"))
        assertTrue(LocalAddress.valid("172.16.255.2"))
        assertTrue(LocalAddress.valid("172.31.0.1"))
        assertFalse(LocalAddress.valid("8.8.8.8"))
        assertFalse(LocalAddress.valid("172.32.0.1"))
        assertFalse(LocalAddress.valid("127.0.0.1"))
        assertFalse(LocalAddress.valid("192.168.00.61"))
        assertFalse(LocalAddress.valid("example.org"))
    }

    @Test fun cameraUrlsRejectCredentialsAndInternet() {
        assertTrue(LocalAddress.validRtsp("rtsp://192.168.0.20:554/stream"))
        assertFalse(LocalAddress.validRtsp("rtsp://user:pass@192.168.0.20/stream"))
        assertFalse(LocalAddress.validRtsp("rtsp://192.168.0.20/live?token=secret"))
        assertFalse(LocalAddress.validRtsp("rtsp://8.8.8.8/stream"))
        assertFalse(LocalAddress.validRtsp("https://192.168.0.20/stream"))
    }
}
