package ua.zilla.energy.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Source-only instrumented tests; not executed or compiled yet. */
@RunWith(AndroidJUnit4::class)
class EnergyDatabaseTest {
    private lateinit var db: EnergyDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EnergyDatabase::class.java
        ).build()
    }

    @After fun shutdown() { db.close() }

    @Test fun bucketsIsolateDeviceEndpoints() = runBlocking {
        val dao = db.dao()
        fun row(endpoint: String, at: Long, value: Double) = TelemetryRow(
            endpoint = endpoint, timestampMs = at, soc = 80,
            batteryW = value, gridW = value, offgridW = null, solarW = null,
            capacityWh = null, gridImportWh = null, gridExportWh = null,
            solarEnergyWh = null, loadWh = null
        )
        dao.insertSample(row("a", 600_000, 100.0))
        dao.insertSample(row("a", 660_000, 300.0))
        dao.insertSample(row("b", 600_000, 999.0))
        val samples = dao.buckets("a", 0, 300_000)
        assertEquals(1, samples.size)
        assertEquals(200.0, samples.single().batteryW!!, 0.01)
    }

    @Test fun countsPhoneLossWithoutCallingItDeviceFailure() = runBlocking {
        val dao = db.dao()
        dao.insertProbe(ProbeRow(endpoint = "a", timestampMs = 1,
            result = "ok", latencyMs = 32, reasonCode = null))
        dao.insertProbe(ProbeRow(endpoint = "a", timestampMs = 2,
            result = "timeout", latencyMs = null, reasonCode = null))
        dao.insertProbe(ProbeRow(endpoint = "a", timestampMs = 3,
            result = "phone_wifi", latencyMs = null, reasonCode = null))
        val result = dao.probeSummary("a", 0)
        assertEquals(3L, result.attempts)
        assertEquals(1L, result.successes)
        assertEquals(1L, result.timedOut)
        assertEquals(1L, result.phoneWifiMissing)
        dao.pruneProbes(3)
        assertEquals(1L, dao.probeSummary("a", 0).attempts)
    }
}
