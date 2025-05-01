package cs.ok3vo.five9record.util

import android.annotation.SuppressLint
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("WakelockTimeout")
fun AtomicReference<WakeLock?>.acquire(powerManager: PowerManager, tag: String) {
    val lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
        .apply { acquire() }
    this.getAndSet(lock)?.apply { release() }
}

fun AtomicReference<WakeLock?>.release() {
    this.getAndSet(null)?.apply { release() }
}
