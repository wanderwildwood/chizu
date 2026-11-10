package com.wanderwildwood.chizu.ui

import android.view.View
import android.widget.TextView
import app.organicmaps.R
import com.wanderwildwood.chizu.GoPlaces

/*
 * Home as the first row of Favorites: it names the place that is Home, or says that none is
 * set. A tap opens the same note as holding the Home button on the map.
 */
object HomeRow {
    @JvmStatic
    fun wire(row: View) {
        row.setOnClickListener { HomeButton.about(row.context) { fill(row) } }
        fill(row)
    }

    @JvmStatic
    fun fill(row: View) {
        val home = GoPlaces.home(row.context)
        row.findViewById<TextView>(R.id.home_place).text =
            home?.name?.takeIf { it.isNotBlank() } ?: row.context.getString(R.string.go_home_unset)
    }
}
