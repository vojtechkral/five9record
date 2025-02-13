package cs.ok3vo.five9record.radio.rig

import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.OpenSerialDevice
import cs.ok3vo.five9record.radio.RadioCompanion
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.radio.RadioIo
import kotlin.random.Random

class MockedRadio: RadioIo {
    override fun readRadioData() = mockRadioData()

    override fun close() {}

    companion object: RadioCompanion {
        override val name = "Emulated radio"
        override val baudRates = arrayOf(4800)
        override val baudRateHint = null

        // Mocked radio cannot be started with a real serial port.
        override fun startIo(serial: OpenSerialDevice, baudRate: Int) = throw NotImplementedError()

        fun mockRadioData() = RadioData(
            rig = name,
            freq = (14200000L..14350000L).random(), // TODO: make smoother changes
            mode = Mode.USB,
            tx = Random.nextBoolean(),
            power = 40,
        )
    }
}
