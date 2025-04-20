package cs.ok3vo.five9record.recording

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.databinding.ActivityStartRecordingBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.radio.RadioType
import cs.ok3vo.five9record.radio.SerialDevice
import cs.ok3vo.five9record.radio.CatSerialError
import cs.ok3vo.five9record.radio.Usb
import cs.ok3vo.five9record.ui.PickerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StartRecordingState(
    serialDevices: List<SerialDevice>,
    audioDevices: List<AudioDeviceInfoUi>,
): ViewModel() {
    var radioType by mutableStateOf(RadioType.entries.first())
        private set
    var baudRates by mutableStateOf(radioType.companion.baudRates.toList())
    var baudRate by mutableIntStateOf(baudRates.first())
    var serialDevice by mutableStateOf(serialDevices.firstOrNull())
    var audioDevice by mutableStateOf(audioDevices.firstOrNull())

    fun updateRadioType(updated: RadioType) {
        radioType = updated
        baudRates = updated.companion.baudRates.toList()
    }
}

class StartRecordingActivity: AppCompatActivity() {
    private val binding: ActivityStartRecordingBinding by lazy {
        ActivityStartRecordingBinding.inflate(layoutInflater)
    }
    private val usb: Usb by lazy { Usb(this) }
    private val audioDevs: AudioDevices by lazy { AudioDevices(this) }
    private val permsRequestResults = Channel<IntArray>()

    private val serialDevices by lazy { usb.serialDevices }
    private val audioDevices by lazy { audioDevs.recordingDevices }
    private val state by lazy { StartRecordingState(serialDevices, audioDevices) }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Radio.isRunning) {
            val intent = Intent(this, RecordingActivity::class.java)
            startActivity(intent)
            finish()
        }

        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.composeView.setContent {
            MaterialTheme { // TODO: use app's theme
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.new_recording)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            )
                        )
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            icon = { Icon(painterResource(R.drawable.voicemail), "Record") },
                            text = { Text(stringResource(R.string.start_recording)) },
                            onClick = {
                                lifecycleScope.launch { startRecording() }
                            }
                        )
                    }
                ) {
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                    ) {
                        PickerItem(
                            icon = ImageVector.vectorResource(R.drawable.trx),
                            title = stringResource(R.string.radio),
                            items = RadioType.entries,
                            selectedItem = state.radioType,
                            emptyText = "No Radio Types", // does not happen
                            onItemSelected = state::updateRadioType,
                        )
                        PickerItem(
                            icon = ImageVector.vectorResource(R.drawable.usb_connection),
                            title = stringResource(R.string.serial_port),
                            items = serialDevices,
                            selectedItem = state.serialDevice,
                            emptyText = stringResource(R.string.no_serial_ports_found),
                            onItemSelected = { state.serialDevice = it },
                        )
                        PickerItem(
                            icon = ImageVector.vectorResource(R.drawable.bandwidth),
                            title = stringResource(R.string.baud_rate),
                            items = state.baudRates,
                            selectedItem = state.baudRate,
                            emptyText = stringResource(R.string.no_serial_ports_found),
                            itemLabel = { "$it bps" },
                            onItemSelected = { state.baudRate = it },
                        )
                        PickerItem(
                            icon = ImageVector.vectorResource(R.drawable.jack_connector),
                            title = stringResource(R.string.audio_input),
                            items = audioDevices,
                            selectedItem = state.audioDevice,
                            emptyText = stringResource(R.string.no_audio_devs_found),
                            divider = false,
                            onItemSelected = { state.audioDevice = it },
                        )
                    }
                }
            }
        }
    }

    private suspend fun startRecording() {
        if (!ensurePermissions()) {
            return
        }

        val radio = state.radioType
        val serial = state.serialDevice
        if (serial == null && radio != RadioType.MOCKED) {
            MaterialAlertDialogBuilder(this)
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

        val audio = state.audioDevice
        if (audio == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cannot start recording")
                .setMessage("Cannot start recording without an audio input device.")
                .setPositiveButton("Back") { dialog, _ -> dialog.dismiss() }
                .show()

            return
        }

        val startupData = RecordingService.StartupData(
            audioDevice = audio.id,
        )
        val svcIntent = Intent(this, RecordingService::class.java)
            .apply { putExtra(RecordingService.INTENT_STARTUP_DATA, startupData) }
        val activityIntent = Intent(this, RecordingActivity::class.java)

        val dgConnecting = MaterialAlertDialogBuilder(this)
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

            MaterialAlertDialogBuilder(this)
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
        if (radio != RadioType.MOCKED) {
            val openSerial = usb.openSerial(serial!!) // serial known to be non-null at this point
            withContext(Dispatchers.IO) {
                Radio.start(openSerial, radio, state.baudRate)
            }
        } else {
            Radio.startMocked()
            delay(1_000)
        }
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

                    MaterialAlertDialogBuilder(this)
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
