package cs.ok3vo.five9record.ui

import android.Manifest.permission
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cs.ok3vo.five9record.R

private val neededPerms = mutableListOf(
        permission.RECORD_AUDIO,
        permission.ACCESS_COARSE_LOCATION,
        permission.ACCESS_FINE_LOCATION,
    ).apply {
        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            add(permission.POST_NOTIFICATIONS)
        }
    }.toList()

@Composable
fun EnsurePermissions(
    onGranted: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var nonGrantedPerms by remember { mutableStateOf(listOf<String>()) }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        grantedMap ->

        // Don't fail if fine location was not granted
        val notGranted = grantedMap.filter {
            (perm, granted) ->
            perm != permission.ACCESS_FINE_LOCATION && !granted
        }

        if (notGranted.isNotEmpty()) {
            nonGrantedPerms = notGranted.keys.toList()
        } else {
            onGranted(true)
        }
    }

    if (nonGrantedPerms.isNotEmpty()) {
        val perms = nonGrantedPerms
            .joinToString(separator = "\n") {
                val name = it.split('.').last()
                "${Typography.nbsp}${Typography.bullet} $name"
            }

        AlertDialog(
            onDismissRequest = { onGranted(false) },
            title = { Text(stringResource(R.string.permission_error)) },
            text = {
                Column {
                    Text(stringResource(R.string.permissions_not_granted))
                    Spacer(Modifier.height(5.dp))
                    Text(perms)
                    Spacer(Modifier.height(5.dp))
                    Text(stringResource(R.string.recording_start_error_permissions))
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(onClick = { onGranted(false) }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    } else {
        LaunchedEffect(Unit) {
            val neededPerms = context.filterNongrantedPermissions(neededPerms)
            permsLauncher.launch(neededPerms.toTypedArray())
        }
    }
}

fun Context.filterNongrantedPermissions(perms: List<String>)
    = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
