package ua.zilla.energy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.zilla.energy.model.ConnectionStatus

class ConnectionTrackerTest {
    @Test fun oneTimeoutIsNotOffline() {
        val tracker = ConnectionTracker(offlineAfter = 3)
        assertEquals(ConnectionStatus.ONLINE, tracker.observe(true, 0)?.status)
        assertEquals(ConnectionStatus.DEGRADED, tracker.observe(false, 30_000)?.status)
        assertNull(tracker.observe(false, 60_000))
        assertEquals(ConnectionStatus.OFFLINE, tracker.observe(false, 90_000)?.status)
        assertEquals(3, tracker.misses)
    }

    @Test fun recoversWithEstimatedDuration() {
        val tracker = ConnectionTracker(3)
        tracker.observe(true, 0)
        tracker.observe(false, 30_000)
        tracker.observe(false, 60_000)
        tracker.observe(false, 90_000)
        val recovered = tracker.observe(true, 120_000)
        assertEquals(ConnectionStatus.ONLINE, recovered?.status)
        assertEquals(90L, recovered?.seconds)
        assertEquals(0, tracker.misses)
        assertNull(tracker.observe(true, 121_000))
    }
}
