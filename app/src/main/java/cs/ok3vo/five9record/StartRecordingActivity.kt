package cs.ok3vo.five9record

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cs.ok3vo.five9record.databinding.ActivityStartRecordingBinding
import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.radio.RadioType
import cs.ok3vo.five9record.radio.SerialDevice
import cs.ok3vo.five9record.radio.CatSerialError
import cs.ok3vo.five9record.radio.Usb
import cs.ok3vo.five9record.recording.AudioDeviceInfoUi
import cs.ok3vo.five9record.recording.AudioDevices
import cs.ok3vo.five9record.recording.RecordingActivity
import cs.ok3vo.five9record.recording.RecordingService
import cs.ok3vo.five9record.ui.PickerItem
import cs.ok3vo.five9record.util.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class StartRecordingData(
    val radioType: RadioType = RadioType.YAESU_FT_891,
    val baudRate: Int = RadioType.YAESU_FT_891.companion.baudRates.first(),
    val audioDevice: Int = -1,
    val locationPrecision: LocationPrecision = LocationPrecision.FULL_LOCATION,
    val locationInMetatrack: Boolean = true,
) {
    companion object {
        val serializer = object: Serializer<StartRecordingData> {
            override val defaultValue = StartRecordingData()

            override suspend fun readFrom(input: InputStream): StartRecordingData {
                return try {
                    val json = input.readBytes().decodeToString()
                    Json.decodeFromString(serializer(), json)
                } catch (e: Exception) {
                    defaultValue
                }
            }

            @Suppress("BlockingMethodInNonBlockingContext")
            override suspend fun writeTo(t: StartRecordingData, output: OutputStream) {
                val json = Json.encodeToString(serializer(), t)
                output.write(json.encodeToByteArray())
            }
        }
    }
}

private val Context.startRecordingDataStore: DataStore<StartRecordingData> by dataStore(
    fileName = "startRecordingData.json",
    serializer = StartRecordingData.serializer,
)

private class StartRecordingState(
    persisted: StartRecordingData,
    serialDevices: List<SerialDevice>,
    audioDevices: List<AudioDeviceInfoUi>,
) {
    var radioType by mutableStateOf(persisted.radioType)
        private set
    var baudRates by mutableStateOf(radioType.companion.baudRates.toList())
    var baudRate by mutableIntStateOf(persisted.baudRate)
    var serialDevice by mutableStateOf(serialDevices.firstOrNull())
    var audioDevice by mutableStateOf(
        audioDevices.find { it.id == persisted.audioDevice }
        ?: audioDevices.firstOrNull()
    )
    var locationPrecision by mutableStateOf(persisted.locationPrecision)
    var locationInMetatrack by mutableStateOf(persisted.locationInMetatrack)

    fun toPersistence() = StartRecordingData(
        radioType = radioType,
        baudRate = baudRate,
        audioDevice = audioDevice?.id ?: -1,
        locationPrecision = locationPrecision,
        locationInMetatrack = locationInMetatrack,
    )

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
    private val state by lazy {
        val persisted = runBlocking { startRecordingDataStore.data.first() }
        StartRecordingState(persisted, serialDevices, audioDevices)
    }

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
                            divider = true,
                            onItemSelected = { state.audioDevice = it },
                        )
                        PickerItem(
                            icon = ImageVector.vectorResource(R.drawable.location_precision),
                            title = stringResource(R.string.qth_precision),
                            items = LocationPrecision.entries,
                            selectedItem = state.locationPrecision,
                            emptyText = "", // does not happen
                            divider = false,
                            additionalDialogRow = {
                                QthAdditionalRow(checked = state.locationInMetatrack) {
                                    checked -> state.locationInMetatrack = checked
                                }
                            },
                            onItemSelected = { state.locationPrecision = it },
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

        startRecordingDataStore.updateData {
            state.toPersistence()
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

        val locationPrecision = state.locationPrecision

        val startupData = RecordingService.StartupData(
            audioDevice = audio.id,
            locationPrecision = locationPrecision,
            locationInMetatrack = state.locationInMetatrack,
        )
        val svcIntent = Intent(this, RecordingService::class.java)
            .apply { putExtra(RecordingService.INTENT_STARTUP_DATA, startupData) }
        val activityIntent = Intent(this, RecordingActivity::class.java)
            .apply { putExtra(RecordingActivity.INTENT_STARTUP_DATA, locationPrecision as Parcelable) }

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

@Composable
private fun QthAdditionalRow(
    checked: Boolean,
    onCheckedChange: (checked: Boolean) -> Unit,
) {
    var checkedState by remember { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = checked,
                onClick = {
                    checkedState = !checkedState
                    onCheckedChange(checkedState)
                },
                role = Role.Checkbox,
            )
            .padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checkedState,
            modifier = Modifier.padding(0.dp),
            onCheckedChange = null,
        )
        Text(
            text = stringResource(R.string.qth_in_metatrack),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.qth_settings_note))
    }
}
