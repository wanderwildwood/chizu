package com.wanderwildwood.chizu

import android.app.Activity
import android.content.Context
import app.organicmaps.R
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.routing.ResultCodes
import app.organicmaps.sdk.routing.RoutingController
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A route from a route server, for when the map a route needs is not on the phone.
 *
 * Every route is tried from the maps on the phone first. When that fails for want of a map, Atlas
 * asks once whether it may send the start and the destination to a route server; after a yes it
 * does so without asking. Settings holds the switch and the address of a server of one's own.
 */
object OnlineRoutes {
    private const val PREFS = "atlas.online"
    private const val KEY_ON = "routes.on" // Absent until the person has answered the question.
    private const val KEY_SERVER = "routes.server"

    /** What the app does with a route that failed, when it is not going online. */
    fun interface Offline {
        /** [asked] is true when the person chose to download the map. */
        fun handle(resultCode: Int, missingMaps: Array<String>, asked: Boolean)
    }

    // The offline result that sent the route online, for "Download the map" after the server failed.
    private var offlineCode = ResultCodes.ROUTE_NOT_FOUND
    private var offlineMaps: Array<String> = emptyArray()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    @JvmStatic
    fun setOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ON, on).apply()

    private fun wasAsked(context: Context): Boolean = prefs(context).contains(KEY_ON)

    /** The address of a server of one's own; empty means the default server. */
    @JvmStatic
    fun server(context: Context): String = prefs(context).getString(KEY_SERVER, "").orEmpty()

    /** Keeps [address] if it is a web address, otherwise goes back to the default server. */
    @JvmStatic
    fun setServer(context: Context, address: String) {
        val clean = cleanServer(address)
        prefs(context).edit().apply { if (clean.isEmpty()) remove(KEY_SERVER) else putString(KEY_SERVER, clean) }.apply()
    }

    @JvmStatic
    fun cleanServer(address: String): String {
        val trimmed = address.trim().trimEnd('/')
        return if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) trimmed else ""
    }

    /** True when [resultCode] says the route could not be made, or made well, for want of a map. */
    @JvmStatic
    fun isMapMissing(resultCode: Int, missingCount: Int): Boolean = when (resultCode) {
        ResultCodes.NEED_MORE_MAPS, ResultCodes.ROUTING_FILE_NOT_EXIST, ResultCodes.INCONSISTENT_MWM_ROUTE -> true
        ResultCodes.ROUTE_NOT_FOUND, ResultCodes.ROUTE_NOT_FOUND_REDRESS_ROUTE_ERROR,
        ResultCodes.START_POINT_NOT_FOUND, ResultCodes.END_POINT_NOT_FOUND,
        ResultCodes.INTERMEDIATE_POINT_NOT_FOUND -> missingCount > 0
        else -> false
    }

    private fun routerCanGoOnline(): Boolean = when (Router.get()) {
        Router.Vehicle, Router.Pedestrian, Router.Bicycle -> true
        else -> false
    }

    /**
     * Called when a route could not be built. Returns true when it is dealt with here (the route is
     * being fetched online, or a dialog is up); false leaves it to the app's own handling.
     */
    @JvmStatic
    fun onBuildError(activity: Activity, resultCode: Int, missingMaps: Array<String>, offline: Offline): Boolean {
        if (Router.isOnline()) {
            // The server's route failed. The next route starts from the maps on the phone again.
            Router.setOnline(false, "")
            if (resultCode == ResultCodes.ROUTE_NOT_FOUND) return false // It answered: there is no such route.
            MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
                .setTitle(R.string.online_routes_failed_title)
                .setMessage(R.string.online_routes_failed_body)
                .setPositiveButton(R.string.online_routes_try_again) { _, _ -> goOnline(activity) }
                .setNegativeButton(R.string.online_routes_download_map) { _, _ ->
                    offline.handle(offlineCode, offlineMaps, true)
                }
                .show()
            return true
        }

        if (!isMapMissing(resultCode, missingMaps.size) || !routerCanGoOnline()) return false

        offlineCode = resultCode
        offlineMaps = missingMaps

        if (!wasAsked(activity)) {
            MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
                .setTitle(R.string.online_routes_ask_title)
                .setMessage(R.string.online_routes_ask_body)
                .setPositiveButton(R.string.online_routes_use) { _, _ ->
                    setOn(activity, true)
                    goOnline(activity)
                }
                .setNegativeButton(R.string.online_routes_download_instead) { _, _ ->
                    setOn(activity, false)
                    offline.handle(resultCode, missingMaps, true)
                }
                .show()
            return true
        }

        if (!isOn(activity)) return false
        goOnline(activity)
        return true
    }

    // Posted: this is reached from inside the routing result, and the new build must start after it.
    private fun goOnline(activity: Activity) {
        val address = server(activity)
        activity.window.decorView.post { RoutingController.get().buildOnline(address) }
    }
}
