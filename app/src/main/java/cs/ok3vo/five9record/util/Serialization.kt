package cs.ok3vo.five9record.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object TimestampSerialize: KSerializer<Instant> {
    private val formatter = DateTimeFormatter.ISO_INSTANT

    override val descriptor get() = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.atOffset(ZoneOffset.UTC).format(formatter))
    }

    override fun deserialize(decoder: Decoder): Instant {
        throw UnsupportedOperationException("Deserialization unsupported")
    }
}
