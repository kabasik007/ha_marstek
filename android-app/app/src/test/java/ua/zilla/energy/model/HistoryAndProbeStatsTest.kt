package ua.zilla.energy.model

import org.junit.Assert.*
import org.junit.Test

class HistoryAndProbeStatsTest {
    @Test fun chartWindowsArePositiveAndBounded() {
        assertEquals(4, HistoryWindow.entries.size)
        HistoryWindow.entries.forEach {
            assertTrue(it.durationMs > 0)
            assertTrue(it.bucketMs > 0)
            assertTrue(it.bucketMs <= it.durationMs)
            // At most 360 buckets for a typical 30-day view.
            assertTrue(it.durationMs / it.bucketMs <= 360)
        }
        assertEquals(86_400_000L, HistoryWindow.DAY.durationMs)
        assertEquals(2_592_000_000L, HistoryWindow.MONTH.durationMs)
    }

    @Test fun observedReachabilityExcludesPhoneWifiLosses() {
        val stats = ProbeStats(
            attempts = 10, successes = 8, timeouts = 0, apiErrors = 0,
            phoneWifiMissing = 2
        )
        assertEquals(100, stats.reachabilityPercent)
    }

    @Test fun apiFailuresCountAgainstOnlyObservedDeviceRequests() {
        val stats = ProbeStats(
            attempts = 20, successes = 14, timeouts = 3,
            apiErrors = 1, phoneWifiMissing = 2
        )
        assertEquals(77, stats.reachabilityPercent)
    }

    @Test fun noProbesMeansUnknownNotPerfect() {
        assertNull(ProbeStats().reachabilityPercent)
        assertNull(ProbeStats(attempts = 5, phoneWifiMissing = 5).reachabilityPercent)
    }
}
