package app.sonyremote.tv

import android.content.Context
import android.hardware.ConsumerIrManager

/** Sends Sony SIRC (12-bit, 40 kHz) frames through the phone's IR blaster. */
class SonyIrRemote(context: Context) {
    private val ir = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val isAvailable: Boolean get() = ir?.hasIrEmitter() == true

    fun send(command: Int, address: Int = TV_ADDRESS, repeats: Int = FRAME_REPEATS) {
        val frame = buildFrame(command, address)
        // Sony expects the frame repeated at least 3 times, 45 ms apart
        val pattern = IntArray(frame.size * repeats) { frame[it % frame.size] }
        ir?.transmit(CARRIER_HZ, pattern)
    }

    // 7 command bits + 5 address bits, LSB first
    private fun buildFrame(command: Int, address: Int): IntArray {
        val pulses = mutableListOf(HEADER_MARK_US, SPACE_US)
        val bits = (0 until 7).map { (command shr it) and 1 } +
            (0 until 5).map { (address shr it) and 1 }
        for (bit in bits) {
            pulses += if (bit == 1) ONE_MARK_US else ZERO_MARK_US
            pulses += SPACE_US
        }
        // Pad the final space so the whole frame lasts 45 ms
        pulses[pulses.lastIndex] += FRAME_PERIOD_US - pulses.sum()
        return pulses.toIntArray()
    }

    companion object {
        const val TV_ADDRESS = 1
        const val FRAME_REPEATS = 3
        private const val CARRIER_HZ = 40_000
        private const val FRAME_PERIOD_US = 45_000
        private const val HEADER_MARK_US = 2_400
        private const val ONE_MARK_US = 1_200
        private const val ZERO_MARK_US = 600
        private const val SPACE_US = 600
    }
}
