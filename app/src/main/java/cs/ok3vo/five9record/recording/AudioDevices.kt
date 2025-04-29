package cs.ok3vo.five9record.recording

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class AudioDeviceInfoUi(private val dev: AudioDeviceInfo) {
    override fun toString() = when (dev.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Builtin mic$address"
        else -> dev.productName.toString()
    }

    val id get() = dev.id
    val type get() = dev.type

    private val address get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        " (${dev.address})"
    } else {
        ""
    }
}

private val AUDIO_TYPES_INTEREST = mutableListOf(
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_AUX_LINE,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    ).also {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            it.add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    }.also {
        it.add(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        it.add(AudioDeviceInfo.TYPE_BUILTIN_MIC)
    }.toTypedArray()

fun Context.getAudioRecordingDevices(): List<AudioDeviceInfoUi> {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    return audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        .filter { AUDIO_TYPES_INTEREST.contains(it.type) }
        .map { AudioDeviceInfoUi(it) }
        .sortedBy { AUDIO_TYPES_INTEREST.indexOf(it.type) }
}
