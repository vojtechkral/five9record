package cs.ok3vo.five9record.recording

import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.Mode
import cs.ok3vo.five9record.radio.RadioData
import cs.ok3vo.five9record.util.TimestampSerialize
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class StatusData(
    val radio: RadioData,
    val location: LocationStatus,

    /** timestamp of the data */
    @Serializable(with = TimestampSerialize::class)
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        val dummyValue = StatusData(
            radio = RadioData(
                rig = "",
                freq = 0,
                mode = Mode.USB,
                tx = false,
                power = 0,
            ),
            location = LocationStatus(),
        )
    }
}
