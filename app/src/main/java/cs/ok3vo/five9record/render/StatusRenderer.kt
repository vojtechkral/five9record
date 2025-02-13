package cs.ok3vo.five9record.render

import android.content.Context
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.recording.StatusData
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class StatusRenderer(private val context: Context) {
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private val inflater = LayoutInflater.from(context)
    private val view = inflater.inflate(R.layout.status_template, null)

    fun render(statusData: StatusData, canvas: Canvas) {
        val sd = statusData
        setText(R.id.rig) { sd.radio.rig }
        setText(R.id.freq) { sd.radio.formatFreq() }
        setText(R.id.mode) { sd.radio.formatMode() }
        setText(R.id.power) { sd.radio.formatPower() }

        setText(R.id.utc) { sd.timestamp.atOffset(ZoneOffset.UTC).format(utcFormatter) }

        val tx = view.findViewById<TextView>(R.id.tx)
        val txLed = view.findViewById<ImageView>(R.id.txLed)
        if (sd.radio.tx) {
            tx.text = "TX"
            txLed.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.status_tx))
        } else {
            tx.text = "RX"
            txLed.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.status_rx))
        }

        setText(R.id.qth) { sd.location.formatQth() }
        setText(R.id.gnssDetail) { sd.location.formatGnssDetail() }

        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.draw(canvas)
    }

    private fun setText(@IdRes id: Int, text: () -> String) {
        view.findViewById<TextView>(id).text = text()
    }

    companion object {
        const val WIDTH = 640
        const val HEIGHT = 480
    }
}

fun RadioData.formatFreq(): String {
    val f = freq
    val mhz = f / 1_000_000
    val khz = (f % 1_000_000) / 1_000
    val hz = f % 1_000

    val group = { num: Long -> String.format(Locale.ROOT, "%03d", num) }

    return if (mhz > 0) {
        "${mhz}.${group(khz)}.${group(hz)} Hz"
    } else if (khz > 0) {
        "${khz}.${group(hz)} Hz"
    } else {
        "$hz Hz"
    }
}

fun RadioData.formatMode() = when (mode) {
    Mode.CW -> "CW"
    Mode.LSB -> "LSB"
    Mode.USB -> "USB"
    Mode.AM -> "AM"
    Mode.FM -> "FM"
    Mode.DATA -> "DATA"
    Mode.RTTY -> "RTTY"
    Mode.OTHER -> "Unknown"
}

fun RadioData.formatPower() = "${power}W"

fun LocationStatus.Position.formatLat(): String {
    var lat = latitude
    var d = 'N'
    if (lat < 0.0) {
        lat = -lat
        d = 'S'
    }

    return "%.5f%C".format(Locale.ROOT, lat, d)
}

fun LocationStatus.Position.formatLon(): String {
    var lon = longitude
    var d = 'E'
    if (lon < 0.0) {
        lon = -lon
        d = 'W'
    }

    return "%.5f%C".format(Locale.ROOT, lon, d)
}

fun LocationStatus.formatQth(): String {
    if (!gnssEnabled) {
        return "N/A"
    }

    return position?.toMaidenhead() ?: "Acquiring…"
}

fun LocationStatus.formatGnssDetail(): String {
    if (!gnssEnabled) {
        return "GNSS location disabled"
    }

    val pos = position
    val satsFix = numSatellites.usedInFix
    val satsTotal = numSatellites.total

    if (pos == null) {
        return "$satsFix/$satsTotal"
    }

    val lat = pos.formatLat()
    val lon = pos.formatLon()
    val accuracy = pos.accuracyRadius?.let { " ±%.1fm".format(Locale.ROOT, it) } ?: ""

    return "$satsFix/$satsTotal $lat $lon$accuracy"
}
