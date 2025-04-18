package cs.ok3vo.five9record.util

import java.time.Duration
import java.time.Instant

fun Instant.elapsed() = Duration.between(this, Instant.now())
