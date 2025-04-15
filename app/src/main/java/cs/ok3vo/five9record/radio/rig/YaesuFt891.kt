package cs.ok3vo.five9record.radio.rig

import com.hoho.android.usbserial.driver.SerialTimeoutException
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import cs.ok3vo.five9record.radio.AsciiCatCmd
import cs.ok3vo.five9record.radio.AsciiCatListener
import cs.ok3vo.five9record.radio.AsciiCatQuery
import cs.ok3vo.five9record.radio.CatSerialError
import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.OpenSerialDevice
import cs.ok3vo.five9record.radio.RadioCompanion
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.radio.RadioIdMismatch
import cs.ok3vo.five9record.radio.RadioIo
import cs.ok3vo.five9record.radio.waitForState
import cs.ok3vo.five9record.util.logE
import java.io.IOException

class YaesuFt891(
    private val serial: OpenSerialDevice,
    baudRate: Int,
): RadioIo {
    private val catListener = AsciiCatListener(delimiter = ';')
    private val ioManager: SerialInputOutputManager

    init {
        serial.port.setParameters(baudRate, 8, 2, UsbSerialPort.PARITY_NONE)
        // By default RTS must be held for FT-891 to respond
        serial.port.rts = true

        ioManager = SerialInputOutputManager(serial.port, catListener).apply {
            // Read timeout in milliseconds, must be configured otherwise the USB thread can't be stopped
            readTimeout = 100
            writeTimeout = TIMEOUT
            start()
            // wait until the USB thread starts up
            waitForState(SerialInputOutputManager.State.RUNNING)
        }

        checkRadio()
    }

    override fun readRadioData(): RadioData {
        val info = catQuery(Cat.Info)
        val tx = catQuery(Cat.Tx)
        val power = catQuery(Cat.ConfigPower.Query(info.menuPowerEntry))

        if (info.mode.isAmOrSsb()) {
            ensureMonitorOn()
        }

        return RadioData(
            rig = name,
            freq = info.freq,
            mode = info.mode,
            tx = tx.tx,
            power = power.power,
        )
    }

    override fun close() {
        ioManager.stop()
        ioManager.waitForState(SerialInputOutputManager.State.STOPPED)
        serial.close()
    }

    private fun checkRadio() {
        val resp = catQuery(Cat.Identification)
        if (resp.id != CAT_ID) {
            throw RadioIdMismatch(name)
        }
    }

    /**
     * If monitor is turned off, turn it on at level zero.
     * This is done for SSB & AM to ensure audio on the DATA connector.
     */
    private fun ensureMonitorOn() {
        val monitor = catQuery(Cat.MonitorSwitch)
        if (!monitor.on) {
            catCmd(Cat.MonitorLevel(0))
            catCmd(Cat.MonitorSwitch(true))
        }
    }

    private fun catCmd(cmd: AsciiCatCmd) {
        val raw = cmd.cmdData()
        try {
            serial.port.write(raw.toByteArray(Charsets.US_ASCII), TIMEOUT)
        } catch (e: IOException) {
            throw CatSerialError()
        }
    }

    private inline fun <reified Resp> catQuery(query: AsciiCatQuery<Resp>): Resp {
        try {
            val cmd = query.queryCmdData()
            serial.port.write(cmd.toByteArray(Charsets.US_ASCII), TIMEOUT)
        } catch (e: SerialTimeoutException) {
            logE("serial port timeout")
            throw e
        } catch (e: IOException) {
            logE("serial port I/O error", e)
            throw e
        }

        val response = catListener.getResponse(TIMEOUT).getOrThrow()
        return query.parseResponse(response)
            ?: throw CatSerialError("Failed to parse response as ${Resp::class.simpleName}: `$response`")
    }

    companion object: RadioCompanion {
        override val name = "Yaesu FT-891"
        override val baudRates = arrayOf(
            4800,
            9600,
            19200,
            38400,
        )
        override val baudRateHint = "The baud rate should match FT-891 configuration item 05-06."

        override fun startIo(serial: OpenSerialDevice, baudRate: Int) = YaesuFt891(serial, baudRate)

        private const val CAT_ID = 650

        /** serial port I/O op timeout in ms */
        private const val TIMEOUT = 2_000
    }
}

private class Cat {
    data class Identification(val id: Int) {
        companion object: AsciiCatQuery<Identification> {
            private val re = """ID(\d{4});""".toRegex()

            override fun queryCmdData() = "ID;"

            override fun parseResponse(response: String): Identification? {
                val match = re.matchEntire(response) ?: return null
                val (id) = match.destructured
                return id.toIntOrNull()?.let { Identification(it) }
            }
        }
    }

    data class Info(
        val freq: Long,
        val mode: Mode,
    ) {
        val menuPowerEntry get() = if (freq < 50_000_000) {
            when (mode) {
                Mode.LSB, Mode.USB -> 1601
                Mode.AM -> 1602
                else -> 1603
            }
        } else {
            when (mode) {
                Mode.LSB, Mode.USB -> 1604
                Mode.AM -> 1605
                else -> 1606
            }
        }

        companion object: AsciiCatQuery<Info> {
            private val re = """IF...(\d{9})[+-]\d{5}0(\w)..00.;""".toRegex()

            override fun queryCmdData(): String = "IF;"

            override fun parseResponse(response: String): Info? {
                val match = re.matchEntire(response) ?: return null
                val (freq, mode) = match.destructured

                return Info(
                    freq = freq.toLongOrNull() ?: return null,
                    mode = Mode.fromFt891Info(mode),
                )
            }
        }
    }

    data class Tx(val tx: Boolean) {
        companion object: AsciiCatQuery<Tx> {
            override fun queryCmdData() = "TX;"

            override fun parseResponse(response: String) =
                when (response) {
                    "TX0;" -> Tx(false)
                    "TX1;", "TX2;" -> Tx(true)
                    else -> null
                }
        }
    }

    data class ConfigPower(val power: Int) {
        class Query(private val item: Int): AsciiCatQuery<ConfigPower> {
            private val re = """EX....(\d{3});""".toRegex()

            override fun queryCmdData() = "EX${item};"

            override fun parseResponse(response: String): ConfigPower? {
                val match = re.matchEntire(response) ?: return null
                val (power) = match.destructured

                return power.toIntOrNull()?.let { ConfigPower(it) }
            }
        }
    }

    data class MonitorSwitch(val on: Boolean): AsciiCatCmd {
        override fun cmdData() = if (on) {
            "ML0001;"
        } else {
            "ML0000;"
        }

        companion object: AsciiCatQuery<MonitorSwitch> {
            private val re = """ML0(\d{3});""".toRegex()

            override fun queryCmdData() = "ML0;"

            override fun parseResponse(response: String): MonitorSwitch? {
                val match = re.matchEntire(response) ?: return null
                val (state) = match.destructured
                val on = when (state) {
                    "000" -> false
                    "001" -> true
                    else -> return null
                }
                return MonitorSwitch(on)
            }
        }
    }

    data class MonitorLevel(val level: Int): AsciiCatCmd {
        override fun cmdData() = "ML1%03d;".format(level)

        companion object: AsciiCatQuery<MonitorLevel> {
            private val re = """ML1(\d{3});""".toRegex()

            override fun queryCmdData() = "ML1;"

            override fun parseResponse(response: String): MonitorLevel? {
                val match = re.matchEntire(response) ?: return null
                val (level) = match.destructured
                return level.toIntOrNull()?.let { MonitorLevel(it) }
            }
        }
    }
}

private fun Mode.isAmOrSsb() = when(this) {
    Mode.LSB, Mode.USB, Mode.AM -> true
    else -> false
}

private fun Mode.Companion.fromFt891Info(id: String): Mode =
    when (id) {
        "1" -> Mode.LSB
        "2" -> Mode.USB
        "3" -> Mode.CW
        "4" -> Mode.FM
        "5" -> Mode.AM
        "6" -> Mode.RTTY
        "7" -> Mode.CW
        "8" -> Mode.DATA
        "9" -> Mode.RTTY
        "B" -> Mode.FM // narrow FM
        "C" -> Mode.DATA
        "D" -> Mode.AM // narrow AM
        else -> Mode.OTHER
    }

// FIXME: unit test parsing
