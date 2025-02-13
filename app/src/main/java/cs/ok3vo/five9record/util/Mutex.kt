package cs.ok3vo.five9record.util

import java.util.concurrent.locks.ReentrantLock

class Mutex<T>(private val value: T) {
    private val lock = ReentrantLock()

    fun<R> lock(block: T.() -> R): R {
        lock.lock()
        return try {
            block(value)
        } finally {
            lock.unlock()
        }
    }
}
