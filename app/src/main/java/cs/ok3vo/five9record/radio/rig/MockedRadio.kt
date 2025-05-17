package cs.ok3vo.five9record.radio.rig

import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.OpenSerialDevice
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.radio.RadioIo
import cs.ok3vo.five9record.util.logI

class MockedRadio: RadioIo {
    private enum class State {
        Tx,
        Rx,
        Scan,
        ChangeBand,

        ;
        fun next() = when (this) {
            Tx -> Rx
            Rx -> arrayOf(Tx, Scan, ChangeBand).random()
            Scan, ChangeBand -> Rx
        }
    }

    private val demoBands = arrayOf(
        Mode.CW to 3_500_000L..3_700_000L,
        Mode.LSB to 3_620_000L..3_800_000L,
        Mode.CW to 7_000_000L..7_060_000L,
        Mode.LSB to 7_060_000L..7_200_000L,
        Mode.CW to 10_100_000L..10_130_000L,
        Mode.CW to 14_000_000L..14_100_000L,
        Mode.USB to 14_112_000L..14_350_000L,
        Mode.CW to 18_068_000L..18_095_000L,
        Mode.USB to 18_120_000L..18_168_000L,
        Mode.CW to 21_000_000L..21_070_000L,
        Mode.USB to 21_151_000L..21_450_000L,
        Mode.CW to 24_890_000L..24_915_000L,
        Mode.USB to 24_940_000L..24_990_000L,
        Mode.CW to 28_000_000L..28_070_000L,
        Mode.USB to 28_320_000L..29_100_000L,
        Mode.AM to 29_100_000L..29_510_000L,
        Mode.FM to 29_100_000L..29_510_000L,
        Mode.AM to 29_520_000L..29_700_000L,
        Mode.FM to 29_520_000L..29_700_000L,
    )
    private val demoWattages = arrayOf(5, 10, 40, 60, 80, 100)
    private var band = demoBands.random()
    private var power = demoWattages.random()
    private var freq = pickNewFreq()
    private var state = State.Rx
    private var freqInc = 0L

    private var seqNum = 0L
    private val stateChangeRate = 50L

    override fun readRadioData(): RadioData {
        seqNum += 1
        if (seqNum % stateChangeRate == 0L) {
            nextState()
        }

        val tx = state == State.Tx
        if (state == State.Scan) {
            freq += freqInc
        }

        return RadioData(
            rig = name,
            freq = freq,
            mode = band.first,
            tx = tx,
            power = power
        )
    }

    override fun close() {}

    private fun pickNewFreq(): Long {
        var freq = band.second.random()
        freq -= freq % 500
        return freq
    }

    private fun nextState() {
        var nextState = state.next()

        when (nextState) {
            State.Scan -> {
                val targetFreq = pickNewFreq()
                freqInc = (targetFreq - freq) / stateChangeRate
            }
            State.ChangeBand -> {
                band = demoBands.random()
                power = demoWattages.random()
                freq = pickNewFreq()
                nextState = State.Rx
            }
            else -> {}
        }

        logI("next state: $nextState")
        state = nextState
    }

    companion object: RadioIo.Companion {
        override val name = "Emulated radio"
        override val baudRates = arrayOf(9001)
        override val baudRateHint = null

        // Mocked radio cannot be started with a real serial port.
        override fun startIo(serial: OpenSerialDevice, baudRate: Int) = throw NotImplementedError()
    }
}
