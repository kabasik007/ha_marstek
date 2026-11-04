package ua.zilla.energy.data.modbus

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ModbusTcpCodecTest {
    @Test fun requestIsOnlyReadHoldingRegisters() {
        val request = ModbusTcpCodec.buildRead(0x1234, 30100, 2)
        assertArrayEquals(byteArrayOf(
            0x12, 0x34, 0, 0, 0, 6, 1, 3,
            0x75, 0x94.toByte(), 0, 2
        ), request)
        try {
            ModbusTcpCodec.buildRead(1, 42000, 1)
            fail("Write/control registers must not be probed")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun parsesSignedAndUnsignedRegisters() {
        val reply = byteArrayOf(
            0, 7, 0, 0, 0, 7, 1,
            3, 4, 0x14, 0x74, 0xFF.toByte(), 0xF6.toByte()
        )
        val regs = ModbusTcpCodec.decodeResponse(reply, 7, 2)
        assertEquals(5236, regs[0])
        assertEquals(-10, ModbusTcpCodec.signed(regs[1]))
    }

    @Test fun rejectsOtherUnitOrFunctionCodes() {
        val wrongUnit = byteArrayOf(0, 1, 0, 0, 0, 5, 2, 3, 2, 0, 1)
        assertThrows(IOException::class.java) {
            ModbusTcpCodec.decodeResponse(wrongUnit, 1, 1)
        }
        val exception = byteArrayOf(0, 1, 0, 0, 0, 3, 1, 0x83.toByte(), 2)
        assertThrows(IOException::class.java) {
            ModbusTcpCodec.decodeResponse(exception, 1, 1)
        }
    }
}
