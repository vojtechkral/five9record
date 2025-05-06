package cs.ok3vo.five9record.ui.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.recording.RecordingEncoder
import cs.ok3vo.five9record.recording.StatusData
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A View that renders the video screen content.
 *
 * A classic View is used for the video frame rendering rather than a Composable,
 * because Composables are quite tricky to render into a Canvas,
 * particularly without a parent window available.
 */
@SuppressLint("ViewConstructor")
class VideoView(
    context: Context,
    private val locationPrecision: LocationPrecision,
): ConstraintLayout(context) {
    init {
        LayoutInflater.from(context).inflate(R.layout.video_view, this, true);
    }

    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun updateData(statusData: StatusData) {
        val sd = statusData
        setText(R.id.rig) { sd.radio.rig }
        setText(R.id.freq) { sd.radio.formatFreq() }
        setText(R.id.mode) { sd.radio.formatMode() }
        setText(R.id.power) { sd.radio.formatPower() }

        setText(R.id.utc) { sd.timestamp.atOffset(ZoneOffset.UTC).format(utcFormatter) }

        val tx = findViewById<TextView>(R.id.tx)
        val txLed = findViewById<ImageView>(R.id.txLed)
        if (sd.radio.tx) {
            tx.text = "TX"
            txLed.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.status_tx))
        } else {
            tx.text = "RX"
            txLed.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.status_rx))
        }

        setText(R.id.qth) { sd.location.formatQth(locationPrecision) }
        setText(R.id.gnssDetail) { sd.location.formatGnssDetail(locationPrecision) }

    }

    private fun setText(@IdRes id: Int, text: () -> String) {
        findViewById<TextView>(id).text = text()
    }

    fun renderToCanvas(canvas: Canvas) {
        measure(
            MeasureSpec.makeMeasureSpec(RecordingEncoder.VIDEO_W, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(RecordingEncoder.VIDEO_H, MeasureSpec.EXACTLY)
        )
        layout(0, 0, measuredWidth, measuredHeight)
        draw(canvas)
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

fun LocationStatus.formatQth(precision: LocationPrecision): String {
    if (!gnssEnabled) {
        return "N/A"
    }

    return position?.toMaidenhead(precision.subsquareEnabled) ?: "Acquiring…"
}

fun LocationStatus.formatGnssDetail(precision: LocationPrecision): String {
    if (!gnssEnabled) {
        return "GNSS location disabled"
    }

    val pos = position
    val satsFix = numSatellites.usedInFix
    val satsTotal = numSatellites.total

    if (pos == null) {
        return "$satsFix/$satsTotal"
    }

    return if (precision == LocationPrecision.FULL_LOCATION) {
        val lat = pos.formatLat()
        val lon = pos.formatLon()
        val accuracy = pos.accuracyRadius?.let { " ±%.1fm".format(Locale.ROOT, it) } ?: ""

        "$satsFix/$satsTotal $lat $lon$accuracy"
    } else {
        "$satsFix/$satsTotal"
    }
}
