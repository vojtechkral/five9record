package cs.ok3vo.five9record.radio.rig

import cs.ok3vo.five9record.radio.OpenSerialDevice
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

    companion object: RadioIo.Companion {
        override val name = "AOR AR82000"
        override val baudRates = arrayOf(
            4800,
            9600,
            19200,
        )
        override val baudRateHint = null // TODO

        override fun startIo(serial: OpenSerialDevice, baudRate: Int): RadioIo {
            TODO("Not yet implemented")
        }
    }
}
