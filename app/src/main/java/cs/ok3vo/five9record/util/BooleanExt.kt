package cs.ok3vo.five9record.util

inline fun<reified T> Boolean.thenNullable(value: T): T?
    = if (this) {
        value
    } else {
        null
    }
