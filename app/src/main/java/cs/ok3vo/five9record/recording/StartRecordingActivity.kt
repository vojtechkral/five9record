package cs.ok3vo.five9record.recording

import android.Manifest.permission
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.databinding.ActivityStartRecordingBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.radio.RadioType
import cs.ok3vo.five9record.radio.SerialDevice
import cs.ok3vo.five9record.radio.CatSerialError
import cs.ok3vo.five9record.radio.Usb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StartRecordingActivity : AppCompatActivity() {
    private val binding: ActivityStartRecordingBinding by lazy {
        ActivityStartRecordingBinding.inflate(layoutInflater)
    }
    private val usb: Usb by lazy { Usb(this) }
    private val audioDevs: AudioDevices by lazy { AudioDevices(this) }
    private val permsRequestResults = Channel<IntArray>()

    private val radio: RadioType get() = binding.spinnerRadio.selectedItem as RadioType

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Radio.isRunning) {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
            finish()
        }

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.spinnerRadio.adapter = ArrayAdapter(this, R.layout.spinner_item, RadioType.entries)
        binding.spinnerRadio.onItemSelectedListener = onSpinnerRadioSelect

        val serialDevs = usb.serialDevices
        if (serialDevs.isNotEmpty()) {
            binding.spinnerSerial.adapter = ArrayAdapter(this, R.layout.spinner_item, serialDevs)
        }

        refreshBaudRates()

        val audioInputs = audioDevs.recordingDevices
        // FIXME: if empty?
        binding.spinnerAudio.adapter = ArrayAdapter(this, R.layout.spinner_item, audioInputs)

        binding.btnStartRecording.setOnClickListener {
            lifecycleScope.launch { startRecording() }
        }

//        MaterialAlertDialogBuilder(this)
//            .setTitle("Pokus")
//            .setSingleChoiceItems(arrayOf("Jedna", "Dva", "Tři"), 0) { _, _ -> }
//            .setPositiveButton("Ok") { _, _ -> }
//            .setNegativeButton("Cancel") { _, _  ->}
//            .show()
    }

    private suspend fun startRecording() {
        if (!ensurePermissions()) {
            return
        }

        val radio = radio
        val serial = binding.spinnerSerial.selectedItem as? SerialDevice?
        if (serial == null && radio != RadioType.MOCKED) {
            AlertDialog.Builder(this)
                .setTitle("Cannot start recording")
                .setMessage(
                    """Cannot start recording without a connected USB serial port.
                        |
                        |Without a serial port only Emulated radio can be used.
                    """.trimMargin()
                )
                .setPositiveButton("Back") { dialog, _ -> dialog.dismiss() }
                .show()

            return
        }

        val audio = binding.spinnerAudio.selectedItem as AudioDeviceInfoUi
        val svcIntent = Intent(this, RecordingService::class.java)
            .apply { putExtra(RecordingService.INTENT_AUDIO_DEVICE, audio.id) }
        val activityIntent = Intent(this, RecordingActivity::class.java)

        val dgConnecting = AlertDialog
            .Builder(this)
            .setTitle("Connecting...")
            .setMessage("Connecting to $radio")
            .show()

        try {
            startRadioIo(radio, serial)
        } catch (e: Exception) {
            dgConnecting.dismiss()

            val bdHint = radio.companion.baudRateHint
            val hint = if (bdHint != null && e is CatSerialError) {
                "\n\nHint: $bdHint"
            } else {
                ""
            }

            // TODO: A nicer error dialog?
            AlertDialog
                .Builder(this)
                .setTitle("Error")
                .setMessage("Could not connect to radio:\n\n${e.message}.$hint")
                .setPositiveButton("Ok") {
                    dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .show()

            return
        }

        startForegroundService(svcIntent)
        dgConnecting.dismiss()
        startActivity(activityIntent)
        finish()
    }

    /**
     * Async due to permission request ui interaction upon serial opening
     * and due to waiting on Radio.start().
     */
    private suspend fun startRadioIo(radio: RadioType, serial: SerialDevice?) {
        val baudRate = binding.spinnerBaudRate.selectedItem as BaudRateUi

        if (radio != RadioType.MOCKED) {
            val openSerial = usb.openSerial(serial!!) // serial known to be non-null at this point
            withContext(Dispatchers.IO) {
                Radio.start(openSerial, radio, baudRate.rate)
            }
        } else {
            Radio.startMocked()
            delay(1_000)
        }
    }

    private fun refreshBaudRates() {
        val radio = radio.companion
        val rates = radio.baudRates.map { BaudRateUi(it) }
        binding.spinnerBaudRate.adapter = ArrayAdapter(this, R.layout.spinner_item, rates)
    }

    private val onSpinnerRadioSelect = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            refreshBaudRates()
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private suspend fun ensurePermissions(): Boolean {
        val toRequest = mutableListOf<String>()
        val addIfNeeded = {
                perm: String ->
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                toRequest += perm
            }
        }

        // Audio recording
        addIfNeeded(permission.RECORD_AUDIO)

        // Notifications
        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            addIfNeeded(permission.POST_NOTIFICATIONS)
        }

        // GNSS location
        addIfNeeded(permission.ACCESS_COARSE_LOCATION)
        addIfNeeded(permission.ACCESS_FINE_LOCATION)

        if (toRequest.isNotEmpty()) {
            requestPermissions(toRequest.toTypedArray(), 0)
            val results = permsRequestResults.receive()
            results.forEachIndexed {
                i, result ->
                if (result == PackageManager.PERMISSION_DENIED) {
                    val perm = toRequest[i].split('.').last()

                    AlertDialog.Builder(this)
                        .setTitle("Permission Error")
                        .setMessage(
                            """The following permission was not granted:
                                |
                                |$perm
                                |
                                |Recording cannot be started.
                                |App permissions can be configured in system settings.
                            """.trimMargin())
                        .setPositiveButton("Back") { dialog, _ -> dialog.dismiss() }
                        .show()

                    return false
                }
            }
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permsRequestResults.trySend(grantResults)
    }
}

class BaudRateUi(val rate: Int) {
    override fun toString() = "$rate bps"
}
