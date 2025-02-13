package cs.ok3vo.five9record.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.SpinnerAdapter
import androidx.appcompat.widget.AppCompatSpinner
import cs.ok3vo.five9record.R

class EmptyAwareSpinner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.spinnerStyle
): AppCompatSpinner(context, attrs, defStyleAttr) {
    private var emptyViewId: Int = View.NO_ID
    private var emptyView: View? = null

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.EmptyAwareSpinner, 0, 0)
            .apply {
                try {
                    emptyViewId = getResourceId(R.styleable.EmptyAwareSpinner_emptyViewId, View.NO_ID)
                } finally {
                    recycle()
                }
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (emptyView == null && emptyViewId != View.NO_ID) {
            emptyView = (parent as? ViewGroup)?.findViewById(emptyViewId)
        }
        checkIfEmpty()
    }

    override fun setAdapter(adapter: SpinnerAdapter?) {
        super.setAdapter(adapter)
        checkIfEmpty()
    }

    private fun checkIfEmpty() {
        val isEmpty = adapter == null || adapter.count == 0
        visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}
