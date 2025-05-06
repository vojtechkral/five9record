package cs.ok3vo.five9record

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cs.ok3vo.five9record.recording.listRecordings
import cs.ok3vo.five9record.ui.Navigation
import cs.ok3vo.five9record.ui.recording.RecordingScreen
import cs.ok3vo.five9record.ui.RecordingsBrowser
import cs.ok3vo.five9record.ui.RecordingsBrowserSelectionBar
import cs.ok3vo.five9record.ui.start_recording.StartRecordingScreen
import java.io.File

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = Navigation.Main) {
        composable<Navigation.Main> {
            MainScreen(onNavigateStartRecording = {
                navController.navigate(Navigation.StartRecording)
            })
        }
        composable<Navigation.StartRecording> {
            StartRecordingScreen(
                onNavigateRecording = {
                    navController.popBackStack() // Replace the start screen with the recording one
                    navController.navigate(Navigation.Recording)
                },
            )
        }
        composable<Navigation.Recording> {
            RecordingScreen(onStopped = {
                navController.popBackStack()
                navController.navigate(Navigation.Main)
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateStartRecording: () -> Unit,
) = MaterialTheme {
    val context = LocalContext.current
    var recordings by remember { mutableStateOf(context.listRecordings()) }
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var deleteRequested by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        recordings = context.listRecordings()
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
                icon = { Icon(ImageVector.vectorResource(R.drawable.voicemail), "Record") },
                text = { Text(stringResource(R.string.new_recording)) },
                onClick = { onNavigateStartRecording() },
                containerColor = MaterialTheme.colorScheme.primary,
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
                            recordings = context.listRecordings()
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
