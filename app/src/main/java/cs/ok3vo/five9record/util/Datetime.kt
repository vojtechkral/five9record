package cs.ok3vo.five9record.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

// FIXME: is this needed?
object Utc {
    val zone = ZoneId.of("UTC")
    val now: ZonedDateTime get() = Instant.now().atZone(zone)
}
