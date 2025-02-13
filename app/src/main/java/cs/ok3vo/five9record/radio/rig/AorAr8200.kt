package cs.ok3vo.five9record.radio.rig

import cs.ok3vo.five9record.radio.OpenSerialDevice
import cs.ok3vo.five9record.radio.RadioCompanion
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.radio.RadioIo

class AorAr8200(
    private val serial: OpenSerialDevice,
    baudRate: Int,
): RadioIo {
    init {
        TODO()
    }

    override fun readRadioData(): RadioData? {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object: RadioCompanion {
        override val name = "AOR AR82000"
        override val baudRates: Array<Int>
            get() = TODO("Not yet implemented")
        override val baudRateHint: String?
            get() = TODO("Not yet implemented")

        override fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo {
            TODO("Not yet implemented")
        }
    }
}
