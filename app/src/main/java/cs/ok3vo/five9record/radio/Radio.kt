package cs.ok3vo.five9record.radio

import cs.ok3vo.five9record.radio.rig.MockedRadio
import cs.ok3vo.five9record.radio.rig.YaesuFt891
import cs.ok3vo.five9record.util.logI
import kotlinx.serialization.Serializable
import java.io.Closeable

interface RadioIo: Closeable {
    fun readRadioData(): RadioData?

    interface Companion {
        val name: String
        val baudRates: Array<Int>
        /** The string resource id of baud rate hint string, if any. */
        val baudRateHint: Int?

        fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo
    }
}

@Serializable
enum class RadioType {
    YAESU_FT_891,
    // AOR_AR8200,  // TODO: work in progress
    MOCKED,
    ;

    /**
     * Start radio I/O on an open serial port `serial` with preferred `baudRate`.
     *
     * The implementation can perform blocking I/O, this function is called from an I/O thread.
     */
    fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo
        = companion.startIo(serial, baudRate)

    val companion: RadioIo.Companion get() = when (this) {
        YAESU_FT_891 -> YaesuFt891.Companion
        // AOR_AR8200 -> AorAr8200.Companion
        MOCKED -> MockedRadio.Companion
    }

    override fun toString() = companion.name
}

object Radio {
    private var io: RadioIo? = null
    val isRunning get() = io != null

    @Synchronized
    fun start(serial: OpenSerialDevice, type: RadioType, baudRate: Int) {
        if (io != null) {
            throw IllegalStateException("Recording already running")
        }

        logI("Starting I/O for $type")
        io = type.startIo(serial, baudRate)
    }

    @Synchronized
    fun startMocked() {
        if (io != null) {
            throw IllegalStateException("Recording already running")
        }

        logI("Starting MockedRadio")
        io = MockedRadio()
    }

    @Synchronized
    fun readRadioData(): RadioData? = io?.readRadioData()

    @Synchronized
    fun stop() {
        io?.close()
        io = null
    }
}
