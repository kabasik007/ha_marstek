package ua.zilla.energy.data

import ua.zilla.energy.model.ConnectionStatus

/** A UDP timeout is not a confirmed hardware reboot. */
class ConnectionTracker(private val offlineAfter: Int = 3) {
    init { require(offlineAfter > 0) }
    var status = ConnectionStatus.CONNECTING
        private set
    var misses = 0
        private set
    private var firstMissMs: Long? = null

    data class Transition(val status: ConnectionStatus, val seconds: Long? = null)

    fun observe(ok: Boolean, nowMs: Long): Transition? {
        val before = status
        if (ok) {
            misses = 0
            val outageSeconds = firstMissMs?.let { ((nowMs - it) / 1000).coerceAtLeast(0) }
            firstMissMs = null
            status = ConnectionStatus.ONLINE
            return when {
                before == ConnectionStatus.OFFLINE -> Transition(status, outageSeconds)
                before == ConnectionStatus.DEGRADED -> Transition(status, outageSeconds)
                before != status -> Transition(status)
                else -> null
            }
        }
        if (misses == 0) firstMissMs = nowMs
        misses++
        status = if (misses >= offlineAfter) ConnectionStatus.OFFLINE
        else ConnectionStatus.DEGRADED
        return if (status != before) Transition(status) else null
    }
}
