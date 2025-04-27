package cs.ok3vo.five9record

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import cs.ok3vo.five9record.databinding.ActivityMainBinding
import cs.ok3vo.five9record.radio.Radio
import cs.ok3vo.five9record.recording.RecordingActivity
import cs.ok3vo.five9record.recording.recordingsDirectory
import cs.ok3vo.five9record.ui.RecordingsBrowser
import cs.ok3vo.five9record.ui.RecordingsBrowserSelectionBar
import java.io.File

class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.composeView.setContent {
            MaterialTheme { // TODO: use app's theme
                var recordings by remember { mutableStateOf(listRecordings()) }
                var selectedFiles by remember { mutableStateOf(setOf<File>()) }
                var deleteRequested by remember { mutableStateOf(false) }

                LifecycleResumeEffect(Unit) {
                    recordings = listRecordings()
                    onPauseOrDispose {}
                }

                Scaffold(
                    topBar = {
                        if (selectedFiles.isEmpty()) {
                            TopAppBar(
                                title = { Text(stringResource(R.string.app_name)) },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            )
                        } else {
                            RecordingsBrowserSelectionBar(
                                numSelected = selectedFiles.size,
                                onDeleteClick = { deleteRequested = true },
                                onSelectAllClick = { selectedFiles = recordings.toSet() },
                                onCloseClick = { selectedFiles = setOf() },
                            )
                        }
                    },
                    floatingActionButton = {
                        ExtendedFloatingActionButton(
                            icon = { Icon(painterResource(R.drawable.voicemail), "Record") },
                            text = { Text(stringResource(R.string.new_recording)) },
                            onClick = { newRecording() }
                        )
                    }
                ) {
                    padding ->

                    if (recordings.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.no_recordings),
                                modifier = Modifier.alpha(0.6f),
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Spacer(Modifier.height(128.dp)) // bump the label slightly up
                        }
                    } else {
                        RecordingsBrowser(
                            recordings = recordings,
                            selectedFiles = selectedFiles,
                            onSelectedChange = { file, selected ->
                                if (selected) {
                                    selectedFiles += file
                                } else {
                                    selectedFiles -= file
                                }
                            },
                            modifier = Modifier.padding(padding),
                        )
                    }

                    if (deleteRequested) {
                        AlertDialog(
                            onDismissRequest = { deleteRequested = false },
                            title = { Text(text = "Delete selected recordings?") },
                            text = {
                                if (selectedFiles.size == 1) {
                                    val file = selectedFiles.first().name
                                    Text("Are you sure you want to delete recording $file? This cannot be undone.")
                                } else {
                                    Text("Are you sure you want to delete ${selectedFiles.size} recordings? This cannot be undone.")
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        selectedFiles.forEach { file -> file.delete() }
                                        selectedFiles = emptySet()
                                        deleteRequested = false
                                        recordings = listRecordings()
                                    }
                                ) {
                                    Text("Yes")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { deleteRequested = false }
                                ) {
                                    Text("Cancel")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun newRecording() {
        val intent = if (Radio.isRunning) {
            Intent(this, RecordingActivity::class.java)
        } else {
            Intent(this, StartRecordingActivity::class.java)
        }
        startActivity(intent)
    }
}

private fun Context.listRecordings()
    = recordingsDirectory().listFiles { f -> f.extension == "mp4" }
    ?.sortedByDescending { it.lastModified() }
    ?: emptyList()
