package com.wanderwildwood.chizu.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import app.organicmaps.R
import com.wanderwildwood.chizu.StartOn

/*
 * Settings, Interface: "Start on", Map or Directory, two choices side by side with the chosen one
 * in ink (the same row as Navigation units). Kept by StartOn, not by the preference.
 */
@Keep
class StartOnPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_start_on
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val map = holder.findViewById(R.id.start_on_map)
        val directory = holder.findViewById(R.id.start_on_directory)
        show(map, directory)
        map.setOnClickListener { choose(false, map, directory) }
        directory.setOnClickListener { choose(true, map, directory) }
    }

    private fun choose(toDirectory: Boolean, map: View, directory: View) {
        if (StartOn.directory(context) == toDirectory) return
        StartOn.setDirectory(context, toDirectory)
        show(map, directory)
    }

    private fun show(map: View, directory: View) {
        val chosen = StartOn.directory(context)
        map.isSelected = !chosen
        directory.isSelected = chosen
    }
}
