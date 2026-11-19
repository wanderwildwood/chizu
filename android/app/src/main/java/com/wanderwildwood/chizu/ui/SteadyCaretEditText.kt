package com.wanderwildwood.chizu.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import com.google.android.material.textfield.TextInputEditText

/**
 * A text field whose caret does not blink.
 *
 * The platform blinks the caret from a timer, twice a second, for as long as a field has
 * focus, and nothing but hiding it stops that. On an E Ink panel each blink is a redraw. So the
 * platform's caret is switched off and the same caret is drawn here, as a steady bar at the
 * cursor in the text's own color (black, or white in dark mode).
 *
 * Messaging's and Whereabouts' StaticCaretEditText, on TextInputEditText so it sits inside the
 * TextInputLayouts every Atlas field uses: the layouts name this class instead.
 */
open class SteadyCaretEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle,
) : TextInputEditText(context, attrs, defStyleAttr) {

    private val caret = Paint()
    private val caretWidth = 2f * resources.displayMetrics.density

    /** Whether the caret is wanted, as the platform's would have been. Only the drawing changes. */
    private var caretWanted = true

    init {
        // Read here rather than trusted from the constructor: TextView applies the attribute
        // before this class's fields exist.
        caretWanted = context.obtainStyledAttributes(
            attrs, intArrayOf(android.R.attr.cursorVisible), defStyleAttr, 0
        ).run { getBoolean(0, true).also { recycle() } }
        super.setCursorVisible(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        if (!caretWanted || !isFocused || selectionStart < 0 || selectionStart != selectionEnd) return
        val offset = selectionStart
        val line = layout.getLineForOffset(offset)
        val x = totalPaddingLeft + layout.getPrimaryHorizontal(offset)
        val top = totalPaddingTop + layout.getLineTop(line)
        val bottom = totalPaddingTop + layout.getLineBottom(line)
        caret.color = currentTextColor
        canvas.drawRect(x, top.toFloat(), x + caretWidth, bottom.toFloat(), caret)
    }

    // The caret moves without the text changing, and with the platform's caret off nothing
    // else asks for the redraw that moves this one. One frame per move, none while still.
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        invalidate()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    // The platform's caret stays off whatever is asked; what was asked decides whether the
    // steady one is drawn.
    override fun setCursorVisible(visible: Boolean) {
        super.setCursorVisible(false)
        if (caretWanted != visible) {
            caretWanted = visible
            invalidate()
        }
    }
}
