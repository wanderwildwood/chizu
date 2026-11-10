package com.wanderwildwood.chizu.ui

import android.content.Context
import android.view.View
import app.organicmaps.MwmActivity
import app.organicmaps.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wanderwildwood.chizu.GoPlaces

/*
 * The Home button on the map. A tap plans the route home from where the phone is. Held, it says
 * where Home is and can forget it; the Home row of Favorites does the same. Home is set from a place's own page ("Set as Home").
 */
object HomeButton {
    @JvmStatic
    fun wire(button: View, activity: MwmActivity) {
        button.setOnClickListener { tap(activity) }
        button.setOnLongClickListener { about(activity); true }
    }

    private fun tap(activity: MwmActivity) {
        val home = GoPlaces.home(activity)
        if (home == null) explain(activity) else activity.startLocationToPoint(home.copy(name = activity.getString(R.string.go_home)).toMapObject())
    }

    /** Where Home is, with a way to forget it; [changed] runs after it was forgotten. */
    @JvmStatic
    @JvmOverloads
    fun about(activity: Context, changed: Runnable? = null) {
        val home = GoPlaces.home(activity) ?: return explain(activity)
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(R.string.go_home)
            .setMessage(activity.getString(R.string.go_home_is, home.name))
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.go_home_forget) { _, _ ->
                GoPlaces.forgetHome(activity)
                changed?.run()
            }
            .show()
    }

    private fun explain(activity: Context) {
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(R.string.go_home)
            .setMessage(R.string.go_home_not_set)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
