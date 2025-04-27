package cs.ok3vo.five9record.ui

import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import cs.ok3vo.five9record.R
import java.io.File

@Composable
fun RecordingsBrowser(
    recordings: List<File>,
    selectedFiles: Set<File>,
    onSelectedChange: (File, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var playingFile by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current

    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(recordings, key = { it.name }) {
            file ->
            val isSelected = selectedFiles.contains(file)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .combinedClickable(
                        onClick = {
                            if (selectedFiles.isEmpty()) {
                                playingFile = file
                            } else {
                                onSelectedChange(file, !isSelected)
                            }
                        },
                        onLongClick = {
                            if (selectedFiles.isEmpty()) {
                                onSelectedChange(file, true)
                            }
                        },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (playingFile == file) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(modifier = Modifier.padding(8.dp)) {
                    val iconSize = Modifier.size(40.dp, 40.dp)
                    if (selectedFiles.isEmpty()) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.video_file),
                            contentDescription = null,
                            modifier = iconSize,
                        )
                    } else {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = iconSize,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = Formatter.formatShortFileSize(context, file.length()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (playingFile != null) {
        PreviewDialog(file = playingFile!!, onDismiss = { playingFile = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsBrowserSelectionBar(
    numSelected: Int,
    onDeleteClick: () -> Unit,
    onSelectAllClick: () -> Unit,
    onCloseClick: () -> Unit,
) {
    TopAppBar(
        title = { Text("$numSelected selected") },
        actions = {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(onClick = onSelectAllClick) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.checkbox_many_checked),
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel Selection",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun PreviewDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.toUri()))
            prepare()
            playWhenReady = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp
        ) {
            Box {
                AndroidView(
                    factory = {
                        PlayerView(it).apply { player = exoPlayer }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        modifier = Modifier.size(48.dp),
                        contentDescription = "Close",
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
}
