package cs.ok3vo.five9record.ui.recording

import android.os.Environment
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import cs.ok3vo.five9record.R
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.recording.RecordingEncoder
import cs.ok3vo.five9record.recording.RecordingService
import cs.ok3vo.five9record.recording.StatusData
import cs.ok3vo.five9record.ui.video.VideoView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private enum class State {
    GROUND,
    STOP_REQUESTED,
    ERROR,

    ;
    companion object {
        lateinit var error: Exception
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onStopped: () -> Unit,
) = MaterialTheme {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val outputFileRelative = remember {
        val extStorageRoot = Environment.getExternalStorageDirectory().absolutePath
        RecordingService.State.Running.outputFile.absolutePath.removePrefix(extStorageRoot)
    }

    var startTime by remember { mutableStateOf<Instant?>(null) }
    var duration by remember { mutableStateOf(Duration.ZERO) }
    var outFileSize by remember { mutableStateOf("0b") }
    var showPreview by remember { mutableStateOf(false) }
    var statusData by remember { mutableStateOf(StatusData.dummyValue) }

    var state by remember { mutableStateOf(State.GROUND) }

    LifecycleStartEffect(Unit) {
        scope.launch {
            while (true) {
                when (RecordingService.state) {
                    RecordingService.State.StartingUp -> { /* delay */ }

                    RecordingService.State.Running -> {
                        statusData = RecordingService.State.Running.statusData.get()

                        if (startTime == null) {
                            startTime = statusData.timestamp
                        } else {
                            duration = Duration.between(startTime, statusData.timestamp)
                        }
                    }

                    RecordingService.State.Error -> RecordingService.takeError()?.let {
                        error ->
                        Log.e("RecordingScreen", "recording stopped with a failure", error)
                        State.error = error
                        state = State.ERROR
                        return@launch
                    }

                    RecordingService.State.Stopped -> {
                        onStopped()
                        return@launch
                    }
                }

                val size = RecordingService.State.Running.outputFile.length()
                outFileSize = Formatter.formatShortFileSize(context, size)
                delay(100)
            }
        }

        onStopOrDispose {}
    }

    BackHandler {
        state = State.STOP_REQUESTED
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
                icon = { Icon(ImageVector.vectorResource(R.drawable.stop), "Stop recording") },
                text = { Text(stringResource(R.string.stop_recording)) },
                onClick = { state = State.STOP_REQUESTED },
                containerColor = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(10.dp)
        ) {
            val rowPadding = 5.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.voicemail),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.recording_ongoing),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Row(
                modifier = Modifier.padding(bottom = rowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(stringResource(R.string.duration))
                Text(
                    text = duration.formatRecording(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            Row(modifier = Modifier.padding(bottom = rowPadding)) {
                Label(stringResource(R.string.file))
                Text(outputFileRelative)
            }
            Row(modifier = Modifier.padding(bottom = rowPadding)) {
                Label(stringResource(R.string.size))
                Text(outFileSize)
            }
            Row(
                modifier = Modifier.padding(bottom = rowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Label(stringResource(R.string.preview))
                Switch(
                    checked = showPreview,
                    onCheckedChange = { showPreview = it },
                )
            }

            if (showPreview) {
                Spacer(Modifier.width(30.dp))

                // Show preview frame, scaling it to fit the available space:
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .background(Color.Black)
                ) {
                    val currentDensity = LocalDensity.current
                    val boxWidth = maxWidth.value * currentDensity.density
                    val boxHeight = maxHeight.value * currentDensity.density
                    val scale = minOf(
                        boxWidth / RecordingEncoder.VIDEO_W,
                        boxHeight / RecordingEncoder.VIDEO_H,
                    )

                    AndroidView(
                        modifier = Modifier.graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                transformOrigin = TransformOrigin(0f, 0f),
                            ),
                        factory = {
                            context -> VideoView(
                                context = context,
                                locationPrecision = RecordingService.State.Running.locationPrecision
                            )
                        },
                        update = { view -> view.updateData(statusData) }
                    )
                }
            }
        }
    }

    when (state) {
        State.GROUND -> {}

        State.STOP_REQUESTED -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.stop_recording_prompt)) },
            text = { Text(stringResource(R.string.stop_recording_prompt_text)) },
            confirmButton = {
                Button(onClick = {
                    Radio.stop()
                    state = State.GROUND
                }) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                Button(onClick = { state = State.GROUND }) {
                    Text(stringResource(R.string.no))
                }
            },
        )

        State.ERROR -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.recording_error)) },
            text = { Text(
                """${stringResource(R.string.recording_error_detail)}
                    |
                    |${State.error.message}
                """.trimMargin()
            ) },
            confirmButton = {},
            dismissButton = {
                Button(onClick = {
                    state = State.GROUND
                    onStopped()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

private val labelColumnWidth = 80.dp

@Composable
private fun Label(text: String, style: TextStyle = LocalTextStyle.current) = Text(
    text = text,
    style = style,
    modifier = Modifier.width(labelColumnWidth),
)

private fun Duration.formatRecording(): String {
    val hours = toHours()
    val minutes = toMinutes() % 60
    val seconds = seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
