package cs.ok3vo.five9record.recording

import cs.ok3vo.five9record.location.LocationStatus
import cs.ok3vo.five9record.radio.RadioData
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class StatusData(
    val radio: RadioData,
    val location: LocationStatus,

    /** timestamp of the data */
    @Serializable(with = TimestampSerialize::class)
    val timestamp: Instant = Instant.now(),
)

private object TimestampSerialize: KSerializer<Instant> {
    private val formatter = DateTimeFormatter.ISO_INSTANT

    override val descriptor get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) {
        encoder.encodeString(value.atOffset(ZoneOffset.UTC).format(formatter))
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant {
        throw UnsupportedOperationException("Deserialization unsupported")
    }
}
