package com.wanderwildwood.chizu.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.organicmaps.R

/*
 * A settings row whose value moves a step at a time: a minus and a plus at its right, the value
 * as the row's second line. It stands where a slider would; the row itself takes no press.
 * The fragment keeps the value: this row only reports the steps.
 */
class StepperPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    fun interface Listener {
        fun onStep(delta: Int)
    }

    var listener: Listener? = null
    private var canGoDown = true
    private var canGoUp = true

    init {
        widgetLayoutResource = R.layout.preference_stepper
        isSelectable = false
        isPersistent = false
    }

    /** Which of the two buttons still has somewhere to go. */
    fun setEnds(down: Boolean, up: Boolean) {
        canGoDown = down
        canGoUp = up
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        bind(holder.findViewById(R.id.step_minus), canGoDown, -1)
        bind(holder.findViewById(R.id.step_plus), canGoUp, 1)
    }

    private fun bind(button: View?, live: Boolean, delta: Int) {
        button ?: return
        button.isEnabled = live
        button.alpha = if (live) 1f else 0.3f
        button.setOnClickListener { listener?.onStep(delta) }
    }
}
