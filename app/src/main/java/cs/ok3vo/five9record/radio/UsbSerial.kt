package cs.ok3vo.five9record.radio

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.getSystemService
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import cs.ok3vo.five9record.util.logE
import cs.ok3vo.five9record.util.logI
import cs.ok3vo.five9record.util.throwError
import kotlinx.coroutines.channels.Channel
import java.io.Closeable

class SerialDevice(
    val driver: UsbSerialDriver,
) {
    override fun toString() = "${driver.device.productName}"
}

class OpenSerialDevice(
    val driver: UsbSerialDriver,
    val connection: UsbDeviceConnection,
): Closeable {
    val port: UsbSerialPort get() = driver.ports[0]

    override fun close() {
        port.close()
        connection.close()
    }
}

class Usb(
    val context: Context,
): BroadcastReceiver() {
    private val ACTION_USB_PERMISSION = "${context.packageName}.USB_PERMISSION"

    private val usbMgr by lazy { context.getSystemService<UsbManager>() }
    private val permissionChan = Channel<Boolean>(Channel.CONFLATED)

    val serialDevices get(): List<SerialDevice> =
        usbMgr
            ?.let { UsbSerialProber.getDefaultProber().findAllDrivers(it) }
            ?.map { SerialDevice(it) }
            .orEmpty()

    /**
     * Async due to permission request ui interaction.
     */
    suspend fun openSerial(device: SerialDevice): OpenSerialDevice {
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiverPreTiramisu(this, filter)
        }

        val intent = Intent(ACTION_USB_PERMISSION)
        intent.setPackage(context.packageName)
        val permissionIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE)
            } else {
                pendingIntentgetBroadcastPreS(context, 0, intent)
            }

        usbMgr?.requestPermission(device.driver.device, permissionIntent)
        val granted = permissionChan.receive()
        return if (granted) {
            usbMgr?.openDevice(device.driver.device)?.let {
                val serial = OpenSerialDevice(
                    driver = device.driver,
                    connection = it,
                )
                serial.port.open(it)
                serial
            } ?: "Could not open USB device".throwError()
        } else {
            val msg = "Permission to access USB was not granted"
            logE(msg)
            msg.throwError()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_USB_PERMISSION) {
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            logI("Permission granted: $granted")
            permissionChan.trySend(granted)

            context.unregisterReceiver(this)
        }
    }
}

// Aliases with suppressed lints for compat:

@SuppressLint("UnspecifiedRegisterReceiverFlag")
private fun Context.registerReceiverPreTiramisu(
    receiver: BroadcastReceiver?,
    filter: IntentFilter?
) = registerReceiver(receiver, filter)

@SuppressLint("UnspecifiedImmutableFlag")
@Suppress("SameParameterValue")
private fun pendingIntentgetBroadcastPreS(
    context: Context, requestCode: Int, intent: Intent,
) = PendingIntent.getBroadcast(context, requestCode, intent, 0)
