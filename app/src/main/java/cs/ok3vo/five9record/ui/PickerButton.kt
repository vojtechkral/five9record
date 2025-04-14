package cs.ok3vo.five9record.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cs.ok3vo.five9record.R

class PickerButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
): MaterialButton(context, attrs, defStyleAttr) {
    private var dialog: AlertDialog? = null
    private val styleAttrs = context.obtainStyledAttributes(attrs, R.styleable.PickerButton)
    private val emptyText = styleAttrs.getString(R.styleable.PickerButton_emptyText) ?: "<No items>"
    private val dialogTitle = styleAttrs.getString(R.styleable.PickerButton_dialogTitle) ?: "Choose an item"
    private var selected = -1
    private var onItemSelectedCb: ((Int, Any?) -> Unit)? = null
    var items = listOf<Any>()
        set(items) {
            field = items
            rebuildItems()
        }
    val selectedItem get() = items.getOrNull(selected)

    init {
        setIconResource(R.drawable.menu)
        setOnClickListener { dialog?.show() }
    }

    private fun rebuildItems() {
        val names = items.map { it.toString() }.toTypedArray()

        if (items.isEmpty()) {
            selected = -1
            dialog = null
            text = emptyText
            isEnabled = false
            return
        } else {
            isEnabled = true
        }

        selected = 0
        updateLabel()
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setSingleChoiceItems(names, 0) {
                dialog, newSel ->
                selected = newSel
                updateLabel()
                dialog.dismiss()
                onItemSelectedCb?.let { it(newSel, selectedItem) }
            }
            .setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    private fun updateLabel() {
        text = items[selected].toString()
    }

    fun onItemSelected(cb: (Int, Any?) -> Unit) {
        onItemSelectedCb = cb
    }
}
