package cs.ok3vo.five9record.util

@Suppress("NOTHING_TO_INLINE")
inline fun String.throwError(): Nothing {
    throw RuntimeException(this)
}
