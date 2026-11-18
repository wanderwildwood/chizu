package com.wanderwildwood.chizu

import android.content.Context
import android.content.Intent
import app.organicmaps.search.SearchActivity

/*
 * Settings, Interface: "Start on" Map or Directory. With Directory, Atlas opens on the search
 * screen's Directory tab, and the map is a tile at its foot. The map stays the root of the task
 * underneath: search needs the map engine (it gives the search its viewport; without it a search
 * waits), so the directory opens once the map engine is up.
 *
 * Back stack with Directory: directory -> Map tile -> map; back on the map -> directory;
 * back on the directory -> Atlas goes to the background. No loop: the directory never opens the
 * map on back, it leaves.
 */
object StartOn {
    private const val PREFS = "atlas"
    private const val KEY = "start_on_directory"

    @JvmStatic
    fun directory(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    @JvmStatic
    fun setDirectory(context: Context, directory: Boolean) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, directory).apply()

    /**
     * Whether the map, now drawn, should open the directory over itself: a fresh start from the
     * launcher with Start on = Directory, or the directory itself coming back after the process
     * was stopped (it went the usual way round through the splash screen, and its intent still
     * says it was the directory).
     */
    @JvmStatic
    fun opensDirectory(context: Context, intent: Intent?, freshStart: Boolean): Boolean {
        if (intent == null) return false
        if (intent.getBooleanExtra(SearchActivity.EXTRA_DIRECTORY_HOME, false)) return true
        return freshStart && Intent.ACTION_MAIN == intent.action &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) && directory(context)
    }
}
