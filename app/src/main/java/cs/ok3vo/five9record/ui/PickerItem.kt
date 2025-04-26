package cs.ok3vo.five9record.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun<T> PickerItem(
    icon: ImageVector,
    title: String,
    items: List<T>,
    selectedItem: T,
    emptyText: String,
    divider: Boolean = true,
    itemLabel: (T) -> String = { it.toString() },
    onItemSelected: (T) -> Unit = {},
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedItemChecked = if (items.contains(selectedItem)) {
        selectedItem
    } else {
        items.firstOrNull()
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose $title") },
            text = {
                Column {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (item == selectedItemChecked),
                                    onClick = {
                                        onItemSelected(item)
                                        showDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item == selectedItemChecked,
                                onClick = null // handled by Row's onClick
                            )
                            Text(
                                text = itemLabel(item),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    SettingsItem(
        title = title,
        value = selectedItemChecked?.let(itemLabel)
            ?: items.firstOrNull()?.let(itemLabel)
            ?: emptyText,
        icon = icon,
        enabled = items.isNotEmpty(),
        divider = divider,
        onClick = { showDialog = true },
    )
}
