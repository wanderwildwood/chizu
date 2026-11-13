package com.wanderwildwood.chizu.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.organicmaps.R
import app.organicmaps.sdk.settings.UnitLocale

/*
 * Settings, Navigation: mph or km/h, two choices side by side with the chosen one in ink. The
 * choice covers distances as well (miles and feet, or kilometers and meters). Nothing is kept
 * here: the value lives in the map core.
 */
@Keep
class UnitsChoicePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_units_choice
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val mph = holder.findViewById(R.id.units_mph)
        val kmh = holder.findViewById(R.id.units_kmh)
        show(mph, kmh)
        mph.setOnClickListener { choose(UnitLocale.UNITS_FOOT, mph, kmh) }
        kmh.setOnClickListener { choose(UnitLocale.UNITS_METRIC, mph, kmh) }
    }

    private fun choose(units: Int, mph: View, kmh: View) {
        if (UnitLocale.getUnits() == units) return
        UnitLocale.setUnits(units)
        show(mph, kmh)
    }

    private fun show(mph: View, kmh: View) {
        val miles = UnitLocale.getUnits() == UnitLocale.UNITS_FOOT
        mph.isSelected = miles
        kmh.isSelected = !miles
    }
}
