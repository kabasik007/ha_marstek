package ua.zilla.energy.data.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Real Marstek LAN measurements only. Demo measurements must never enter this DB.
 *
 * endpoint is a local database partition, not a device identity guarantee.
 * Never publish this DB or the endpoint IP in diagnostic exports.
 */
@Entity(tableName = "telemetry", indices = [Index(value = ["endpoint", "timestampMs"])])
data class TelemetryRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val endpoint: String,
    val timestampMs: Long,
    val soc: Int?,
    val batteryW: Double?,
    val gridW: Double?,
    val offgridW: Double?,
    val solarW: Double?,
    val capacityWh: Double?,
    val gridImportWh: Double?,
    val gridExportWh: Double?,
    val solarEnergyWh: Double?,
    val loadWh: Double?
)

@Entity(tableName = "probes", indices = [Index(value = ["endpoint", "timestampMs"])])
data class ProbeRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val endpoint: String,
    val timestampMs: Long,
    val result: String,
    val latencyMs: Long?,
    val reasonCode: String?
)

/** Aggregated buckets, not fictional/interpolated readings. */
data class EnergyBucket(
    val timestampMs: Long,
    val batteryW: Double?,
    val gridW: Double?,
    val solarW: Double?
)

data class ProbeSummary(
    val attempts: Long,
    val successes: Long,
    val timedOut: Long,
    val apiErrors: Long,
    val phoneWifiMissing: Long,
    val firstProbeMs: Long?,
    val lastProbeMs: Long?
)

@Dao
interface EnergyDao {
    @Insert
    suspend fun insertSample(row: TelemetryRow)

    @Insert
    suspend fun insertProbe(row: ProbeRow)

    @Query(
        """SELECT MIN(timestampMs) AS timestampMs,
                  AVG(batteryW) AS batteryW, AVG(gridW) AS gridW,
                  AVG(solarW) AS solarW
           FROM telemetry
           WHERE endpoint = :endpoint AND timestampMs >= :sinceMs
           GROUP BY (timestampMs / :bucketMs)
           ORDER BY timestampMs ASC"""
    )
    suspend fun buckets(endpoint: String, sinceMs: Long, bucketMs: Long): List<EnergyBucket>

    @Query(
        """SELECT COUNT(*) AS attempts,
                  COALESCE(SUM(CASE WHEN result = 'ok' THEN 1 ELSE 0 END), 0) AS successes,
                  COALESCE(SUM(CASE WHEN result = 'timeout' THEN 1 ELSE 0 END), 0) AS timedOut,
                  COALESCE(SUM(CASE WHEN result = 'api_error' THEN 1 ELSE 0 END), 0) AS apiErrors,
                  COALESCE(SUM(CASE WHEN result = 'phone_wifi' THEN 1 ELSE 0 END), 0) AS phoneWifiMissing,
                  MIN(timestampMs) AS firstProbeMs,
                  MAX(timestampMs) AS lastProbeMs
           FROM probes
           WHERE endpoint = :endpoint AND timestampMs >= :sinceMs"""
    )
    suspend fun probeSummary(endpoint: String, sinceMs: Long): ProbeSummary

    @Query("DELETE FROM telemetry WHERE timestampMs < :cutoffMs")
    suspend fun pruneTelemetry(cutoffMs: Long)

    @Query("DELETE FROM probes WHERE timestampMs < :cutoffMs")
    suspend fun pruneProbes(cutoffMs: Long)
}

@Database(entities = [TelemetryRow::class, ProbeRow::class], version = 1, exportSchema = false)
abstract class EnergyDatabase : RoomDatabase() {
    abstract fun dao(): EnergyDao

    companion object {
        @Volatile private var instance: EnergyDatabase? = null

        fun get(context: Context): EnergyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EnergyDatabase::class.java,
                    "zilla_marstek_history.db"
                ).build().also { instance = it }
            }
    }
}
