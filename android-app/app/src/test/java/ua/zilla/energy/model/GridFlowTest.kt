package ua.zilla.energy.model

import org.junit.Assert.*
import org.junit.Test

class GridFlowTest {
    @Test fun actualVenusV150ChargingSample() {
        val flow = GridFlow.fromRaw(-1740.0)
        assertEquals(GridDirection.IMPORT, flow.direction)
        assertEquals(1740.0, flow.magnitudeW!!, 0.001)
    }

    @Test fun exportKeepsDirectionAndMagnitude() {
        val flow = GridFlow.fromRaw(870.0)
        assertEquals(GridDirection.EXPORT, flow.direction)
        assertEquals(870.0, flow.magnitudeW!!, 0.001)
    }

    @Test fun zeroIsIdleAndMissingIsUnknown() {
        val idle = GridFlow.fromRaw(0.0)
        assertEquals(GridDirection.IDLE, idle.direction)
        assertEquals(0.0, idle.magnitudeW!!, 0.001)
        assertEquals(GridDirection.UNKNOWN, GridFlow.fromRaw(null).direction)
        assertNull(GridFlow.fromRaw(Double.NaN).magnitudeW)
    }
}
