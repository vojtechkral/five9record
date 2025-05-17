package cs.ok3vo.five9record.ui.start_recording

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.lifecycle.compose.LifecycleStartEffect
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.location.LocationPrecision
import cs.ok3vo.five9record.radio.CatSerialError
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.radio.RadioType
import cs.ok3vo.five9record.radio.SerialDevice
import cs.ok3vo.five9record.radio.Usb
import cs.ok3vo.five9record.recording.AudioDeviceInfoUi
import cs.ok3vo.five9record.recording.RecordingService
import cs.ok3vo.five9record.recording.getAudioRecordingDevices
import cs.ok3vo.five9record.recording.recordingFile
import cs.ok3vo.five9record.ui.EnsurePermissions
import cs.ok3vo.five9record.ui.PickerItem
import cs.ok3vo.five9record.util.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

@Serializable
data class SettingsSerde(
    val radioType: RadioType = RadioType.YAESU_FT_891,
    val baudRate: Int = RadioType.YAESU_FT_891.companion.baudRates.first(),
    val audioDevice: Int = -1,
    val locationPrecision: LocationPrecision = LocationPrecision.FULL_LOCATION,
    val locationInMetatrack: Boolean = true,
) {
    companion object {
        val serializer = object: Serializer<SettingsSerde> {
            override val defaultValue = SettingsSerde()

            override suspend fun readFrom(input: InputStream): SettingsSerde {
                return try {
                    val json = input.readBytes().decodeToString()
                    Json.decodeFromString(serializer(), json)
                } catch (e: Exception) {
                    defaultValue
                }
            }

            @Suppress("BlockingMethodInNonBlockingContext")
            override suspend fun writeTo(t: SettingsSerde, output: OutputStream) {
                val json = Json.encodeToString(serializer(), t)
                output.write(json.encodeToByteArray())
            }
        }
    }
}

private val Context.startRecordingDataStore: DataStore<SettingsSerde> by dataStore(
    fileName = "startRecordingData.json",
    serializer = SettingsSerde.serializer,
)

private class Settings(
    persisted: SettingsSerde,
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

    fun toPersistence() = SettingsSerde(
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

private enum class State {
    GROUND,
    RECORDING_REQUESTED,
    RECORDING_STARTING,
    ERROR,

    ;
    lateinit var error: Exception
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartRecordingScreen(
    onNavigateRecording: () -> Unit,
) = MaterialTheme {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val usb = remember { Usb(context) }
    val serialDevices = remember { usb.serialDevices }
    val audioDevices = remember { context.getAudioRecordingDevices() }

    var state by remember { mutableStateOf(State.GROUND) }

    var settings by remember { mutableStateOf(
        Settings(
        persisted = SettingsSerde(),
        serialDevices = serialDevices,
        audioDevices = audioDevices,
    )) }

    LifecycleStartEffect(Unit) {
        if (Radio.isRunning) {
            onNavigateRecording()
        }

        // Load settings
        scope.launch {
            val persisted = context.startRecordingDataStore.data.first()
            settings = Settings(persisted, serialDevices, audioDevices)
        }

        onStopOrDispose {}
    }

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
                icon = { Icon(ImageVector.vectorResource(R.drawable.voicemail), "Record") },
                text = { Text(stringResource(R.string.start_recording)) },
                onClick = { state = State.RECORDING_REQUESTED },
                containerColor = MaterialTheme.colorScheme.primary,
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
                selectedItem = settings.radioType,
                emptyText = "No Radio Types", // does not happen
                onItemSelected = { settings.updateRadioType(it) },
            )
            PickerItem(
                icon = ImageVector.vectorResource(R.drawable.usb_connection),
                title = stringResource(R.string.serial_port),
                items = serialDevices,
                selectedItem = settings.serialDevice,
                emptyText = stringResource(R.string.no_serial_ports_found),
                onItemSelected = { settings.serialDevice = it },
            )
            PickerItem(
                icon = ImageVector.vectorResource(R.drawable.bandwidth),
                title = stringResource(R.string.baud_rate),
                items = settings.baudRates,
                selectedItem = settings.baudRate,
                emptyText = stringResource(R.string.no_serial_ports_found),
                itemLabel = { "$it bps" },
                onItemSelected = { settings.baudRate = it },
            )
            PickerItem(
                icon = ImageVector.vectorResource(R.drawable.jack_connector),
                title = stringResource(R.string.audio_input),
                items = audioDevices,
                selectedItem = settings.audioDevice,
                emptyText = stringResource(R.string.no_audio_devs_found),
                divider = true,
                onItemSelected = { settings.audioDevice = it },
            )
            PickerItem(
                icon = ImageVector.vectorResource(R.drawable.location_precision),
                title = stringResource(R.string.qth_precision),
                items = LocationPrecision.entries,
                selectedItem = settings.locationPrecision,
                emptyText = "", // does not happen
                divider = false,
                additionalDialogRow = {
                    QthAdditionalRow(checked = settings.locationInMetatrack) {
                            checked -> settings.locationInMetatrack = checked
                    }
                },
                onItemSelected = { settings.locationPrecision = it },
            )
        }
    }

    when (state) {
        State.GROUND -> {}

        State.RECORDING_REQUESTED -> EnsurePermissions {
            granted ->
            if (granted) {
                state = State.RECORDING_STARTING
                scope.launch {
                    var error: Exception? = null
                    withContext(Dispatchers.IO) {
                        try {
                            context.startRecording(usb, settings)
                        } catch (e: Exception) {
                            Log.e("StartRecordingScreen", "Error starting recording", e)
                            error = e
                        }
                    }

                    if (error == null) {
                        state = State.GROUND
                        onNavigateRecording()
                    } else {
                        state = State.ERROR.apply { this.error = error!! }
                    }
                }
            } else {
                state = State.GROUND
            }
        }

        State.RECORDING_STARTING -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.connecting_to_radio)) },
            text = { Text(stringResource(R.string.connecting_to_radio_detail, settings.radioType)) },
            confirmButton = {},
            dismissButton = {},
        )

        State.ERROR -> {
            val error = state.error
            val errMsg = error.message ?: "$error"

            val bdHint = settings.radioType.companion.baudRateHint
            val text = if (bdHint != null && error is CatSerialError) {
                val hint = stringResource(bdHint)
                stringResource(R.string.connect_radio_error, errMsg, hint)
            } else {
                errMsg
            }

            AlertDialog(
                onDismissRequest = { state = State.GROUND },
                title = { Text(stringResource(R.string.could_not_start_recording)) },
                text = { Text(text) },
                confirmButton = {},
                dismissButton = {
                    Button(onClick = { state = State.GROUND }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
            )
        }
    }
}

private suspend fun Context.startRecording(usb: Usb, settings: Settings) {
    startRecordingDataStore.updateData {
        settings.toPersistence()
    }

    val radio = settings.radioType
    val serial = settings.serialDevice
    if (serial == null && radio != RadioType.MOCKED) {
        throw RuntimeException(getString(R.string.cannot_start_recording_no_serial))
    }

    val audio = settings.audioDevice
        ?: throw RuntimeException(getString(R.string.cannot_start_recording_no_audio))

    val outputFile = recordingFile(Instant.now())
    val startupData = RecordingService.StartupData(
        audioDevice = audio.id,
        outputFile = outputFile,
        locationPrecision = settings.locationPrecision,
        locationInMetatrack = settings.locationInMetatrack,
    )
    val svcIntent = Intent(this, RecordingService::class.java)
        .apply { putExtra(RecordingService.INTENT_STARTUP_DATA, startupData) }

    if (radio != RadioType.MOCKED) {
        val openSerial = usb.openSerial(serial!!) // serial known to be non-null at this point
        withContext(Dispatchers.IO) {
            Radio.start(openSerial, radio, settings.baudRate)
        }
    } else {
        Radio.startMocked()
        delay(1_000)
    }

    startForegroundService(svcIntent)
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
