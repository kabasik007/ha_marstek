package ua.zilla.energy.data.modbus

import java.io.IOException

/**
 * Strictly read-only Modbus/TCP codec. Only function code 0x03 is emitted.
 * No write function codes (05, 06, 0F, 10, 16 or 17) exist in this module.
 *
 * Marstek Venus E3 community map uses raw PDU register addresses as listed.
 * This is a manual one-shot probe; no background polling is performed.
 */
object ModbusTcpCodec {
    private val allowed = mapOf(
        30100 to 2,   // battery voltage + current
        34003 to 1,   // total cycles
        34018 to 16,  // cell voltage x 16, opt-in only
        35000 to 3,   // internal + MOS1 + MOS2 temperatures
        35010 to 1    // maximum cell temperature
    )

    fun buildRead(transaction: Int, startRegister: Int, count: Int): ByteArray {
        require(transaction in 1..65535)
        require(allowed[startRegister] == count) { "Register range is not in the read-only allowlist" }
        return byteArrayOf(
            (transaction ushr 8).toByte(), transaction.toByte(),
            0, 0,        // protocol ID
            0, 6,        // following bytes: unit ID + FC + start + count
            1,           // Marstek unit/slave ID 1
            3,           // FC03, holding register read
            (startRegister ushr 8).toByte(), startRegister.toByte(),
            (count ushr 8).toByte(), count.toByte()
        )
    }

    /** Parse a complete response: 7-byte MBAP + remaining PDU. */
    fun decodeResponse(frame: ByteArray, transaction: Int, count: Int): List<Int> {
        if (frame.size < 9) throw IOException("Short Modbus response")
        fun u16(offset: Int) = ((frame[offset].toInt() and 255) shl 8) or
            (frame[offset + 1].toInt() and 255)
        if (u16(0) != transaction || u16(2) != 0 || (frame[6].toInt() and 255) != 1) {
            throw IOException("Modbus response transaction/protocol/unit mismatch")
        }
        val length = u16(4)
        if (length != frame.size - 6 || length !in 3..253) throw IOException("Invalid Modbus length")
        val fn = frame[7].toInt() and 255
        if (fn == 0x83) throw IOException("Modbus exception " + (frame[8].toInt() and 255))
        if (fn != 3) throw IOException("Unexpected Modbus function")
        val bytes = frame[8].toInt() and 255
        if (bytes != count * 2 || frame.size != 9 + bytes) {
            throw IOException("Invalid Modbus register count")
        }
        return List(count) { i -> u16(9 + i * 2) }
    }

    fun signed(word: Int): Int = if (word and 0x8000 != 0) word - 65536 else word
}
