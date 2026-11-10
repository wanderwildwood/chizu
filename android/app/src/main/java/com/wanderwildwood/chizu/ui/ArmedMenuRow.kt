package com.wanderwildwood.chizu.ui

import android.widget.TextView
import androidx.activity.ComponentActivity
import app.organicmaps.R

/**
 * A menu row that wants a second tap ("Delete — tap again"). No timer: it stays armed until the
 * second tap, another row, Back, or the menu closing (the menu calls [ArmedWatch.disarm] then,
 * because a menu sheet is its own window and the watch only sees the activity's touches).
 */
class ArmedMenuRow(activity: ComponentActivity, private val title: TextView, private val plain: CharSequence) {
    private val watch = ArmedWatch.of(activity)
    private val disarmRow: () -> Unit = { title.text = plain }

    /** True when this tap is the second one and the action should run. */
    fun tap(): Boolean {
        if (watch.isArmed(disarmRow)) {
            watch.done(disarmRow)
            return true
        }
        title.text = title.context.getString(R.string.armed_tap_again, plain)
        watch.arm(disarmRow)
        return false
    }
}
