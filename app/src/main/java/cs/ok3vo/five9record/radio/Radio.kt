package cs.ok3vo.five9record.radio

import cs.ok3vo.five9record.radio.rig.AorAr8200
import cs.ok3vo.five9record.radio.rig.MockedRadio
import cs.ok3vo.five9record.radio.rig.YaesuFt891
import java.io.Closeable

interface RadioIo: Closeable {
    fun readRadioData(): RadioData?
}

interface RadioCompanion {
    val name: String
    val baudRates: Array<Int>
    val baudRateHint: String?

    fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo
}

enum class RadioType {
    YAESU_FT_891,
    AOR_AR8200,
    MOCKED,
    ;

    /**
     * Start radio I/O on an open serial port `serial` with preferred `baudRate`.
     *
     * The implementation can perform blocking I/O, this function is called from an I/O thread.
     */
    fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo
        = companion.startIo(serial, baudRate)

    val companion: RadioCompanion get() = when (this) {
        YAESU_FT_891 -> YaesuFt891.Companion
        AOR_AR8200 -> AorAr8200.Companion
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

        io = type.startIo(serial, baudRate)
    }

    @Synchronized
    fun startMocked() {
        if (io != null) {
            throw IllegalStateException("Recording already running")
        }

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
