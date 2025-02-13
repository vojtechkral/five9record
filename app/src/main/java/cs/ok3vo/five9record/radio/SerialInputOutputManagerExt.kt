package cs.ok3vo.five9record.radio

import com.hoho.android.usbserial.util.SerialInputOutputManager

fun SerialInputOutputManager.waitForState(state: SerialInputOutputManager.State, intervalMs: Long = 20) {
    while (this.state != state) {
        Thread.sleep(intervalMs)
    }
}
