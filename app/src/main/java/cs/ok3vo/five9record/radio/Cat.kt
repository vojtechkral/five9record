package cs.ok3vo.five9record.radio

import android.util.Log
import com.hoho.android.usbserial.util.SerialInputOutputManager
import cs.ok3vo.five9record.util.logE
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * CAT command data type.
 */
interface CatCmd<out Data> {
    /**
     * Generate command serial line data.
     */
    fun cmdData(): Data
}

/**
 * CAT query data type.
 */
interface CatQuery<Data, out Resp> {
    /**
     * Generate query command serial line data.
     */
    fun queryCmdData(): Data

    /**
     * Parse the response data arriving after issuing the query command.
     */
    fun parseResponse(response: Data): Resp?
}

typealias AsciiCatCmd = CatCmd<String>
typealias AsciiCatQuery<Resp> = CatQuery<String, Resp>
typealias BytesCatCmd = CatCmd<ByteArray>
typealias BytesCatQuery<Resp> = CatQuery<ByteArray, Resp>

/**
 * Reads data from the serial, breaks them up into logical pieces,
 * and queues them up for consumption in another thread.
 */
abstract class CatListener<Data>: SerialInputOutputManager.Listener {
    private val catQueue = ArrayBlockingQueue<Result<Data>>(QUEUE_CAP)

    override fun onRunError(e: Exception) {
        logE("USB serial error", e)
        catQueue.put(Result.failure(e))
    }

    /**
     * Dequeue an expected CAT response. Thread safe.
     */
    fun getResponse(timeoutMs: Int): Result<Data>
        = catQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
        ?: Result.failure(CatSerialError())

    protected fun enqueue(data: Data) {
        if (catQueue.remainingCapacity() > 1) {
            catQueue.put(Result.success(data))
        } else {
            // Capacity running out, the other end is not reading enough.
            catQueue.put(Result.failure(CatSerialError("queue overflow")))
        }
    }

    companion object {
        private const val QUEUE_CAP = 16
    }
}

/**
 * Reads ASCII data from the serial, breaks them up by a delimiter,
 * and queues up for consumption in another thread.
 */
class AsciiCatListener(
    private val delimiter: Char,
): CatListener<String>() {
    private var buffer = ""

    override fun onNewData(data: ByteArray?) {
        buffer += data?.decodeToString() ?: return
        if (buffer.endsWith(delimiter)) {
            // Take buffer, reset it, and enqueue the data onto the cat queue
            val toEnqueue = buffer.also { buffer = "" }
            enqueue(toEnqueue)
        }
    }
}
