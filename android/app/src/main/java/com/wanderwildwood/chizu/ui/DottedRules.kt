package com.wanderwildwood.chizu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import app.organicmaps.R

/**
 * The thin dotted rule between the rows of an ordinary list, inset to the text on both sides
 * (house rule, 3 Nov 2026). Nothing under the last row.
 */
class DottedRules(context: Context) : RecyclerView.ItemDecoration() {
    private val rule = Rule(context)

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val last = (parent.adapter?.itemCount ?: 0) - 1
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position == last) continue
            rule.draw(c, parent.width, child.bottom)
        }
    }

    /**
     * The dotted_rule drawable, drawn once into a bitmap. A dashed line shape only draws its
     * dashes in software, and a list's canvas is a hardware one, so the line is kept as a picture
     * of itself the width of the list and stamped under each row.
     */
    class Rule(context: Context) {
        private val line: Drawable = AppCompatResources.getDrawable(context, R.drawable.dotted_rule)!!
        private val inset = context.resources.getDimensionPixelSize(R.dimen.margin_base)
        private val thick = (context.resources.displayMetrics.density + 0.5f).toInt().coerceAtLeast(1)
        private var stamp: Bitmap? = null

        /** One rule across a list [listWidth] wide, inset on both sides, its top at [top]. */
        fun draw(c: Canvas, listWidth: Int, top: Int) {
            val width = listWidth - 2 * inset
            if (width <= 0) return
            // A line shape draws nothing unless its box is taller than its stroke, so the box
            // is four strokes tall with the line through its middle, and the middle goes on
            // the row's edge.
            val box = thick * 4
            val picture = stamp?.takeIf { it.width == width } ?: Bitmap.createBitmap(width, box, Bitmap.Config.ARGB_8888).also {
                line.setBounds(0, 0, width, box)
                line.draw(Canvas(it))
                stamp = it
            }
            c.drawBitmap(picture, inset.toFloat(), (top - box / 2).toFloat(), null)
        }
    }
}
