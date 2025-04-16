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

        select(0)
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(dialogTitle)
            .setSingleChoiceItems(names, 0) {
                dialog, newSel ->
                select(newSel)
                dialog.dismiss()
                onItemSelectedCb?.let { it(newSel, selectedItem) }
            }
            .setPositiveButton("Ok") { dialog, _ -> dialog.dismiss() }
            .create()
    }

    fun select(itemIndex: Int) {
        if (itemIndex >= 0 && itemIndex < items.size) {
            selected = itemIndex
            text = items[selected].toString()
        }
    }

    fun onItemSelected(cb: (Int, Any?) -> Unit) {
        onItemSelectedCb = cb
    }

    inline fun<reified T> selectItemIf(predicate: (T) -> Boolean) {
        for ((i, item) in items.withIndex()) {
            val itemT = item as? T
            if (itemT != null && predicate(itemT)) {
                select(i)
                return
            }
        }
    }
}
