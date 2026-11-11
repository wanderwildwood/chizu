package com.wanderwildwood.chizu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.location.LocationState
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.util.LocationUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Help while the phone looks for its position. When the map has waited a while for a first fix, a
 * line on the map says so, with how many satellites the phone sees and uses; a press on it explains
 * what helps. Nothing shows once there is a position.
 */
class FixHelp(private val activity: Activity, private val line: TextView) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = activity.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var listening = false
    private var waitingSince = 0L
    private var inView = -1
    private var inUse = 0

    private val satellites = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            inView = status.satelliteCount
            inUse = (0 until status.satelliteCount).count { status.usedInFix(it) }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        line.setOnClickListener { explain() }
    }

    fun onResume() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    fun onPause() {
        handler.removeCallbacks(tick)
        listen(false)
        waitingSince = 0L
        line.visibility = View.GONE
    }

    private fun update() {
        val waiting = LocationState.getMode() == LocationState.PENDING_POSITION &&
            LocationUtils.checkFineLocationPermission(activity) &&
            LocationUtils.areLocationServicesTurnedOn(activity) &&
            !RoutingController.get().isPlanning && !RoutingController.get().isNavigating
        if (!waiting) {
            waitingSince = 0L
            listen(false)
            line.visibility = View.GONE
            return
        }
        val now = System.currentTimeMillis()
        if (waitingSince == 0L) waitingSince = now
        listen(true)
        if (!isSlow(now - waitingSince)) return
        line.text = lineText(activity, inView, inUse)
        line.visibility = View.VISIBLE
    }

    @SuppressLint("MissingPermission") // Checked in update(): this is only reached with the permission.
    private fun listen(on: Boolean) {
        if (on == listening) return
        listening = on
        try {
            if (on) manager?.registerGnssStatusCallback(satellites, handler)
            else manager?.unregisterGnssStatusCallback(satellites)
        } catch (e: RuntimeException) {
            // No satellite report on this phone: the line still says it is looking.
            inView = -1
        }
        if (!on) inView = -1
    }

    private fun explain() {
        val last = MwmApplication.from(activity).locationHelper.savedLocation
        val minutes = last?.let { ((System.currentTimeMillis() - it.time) / 60_000L).toInt() }
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(R.string.fix_help_title)
            .setMessage(activity.getString(R.string.fix_help_body) + "\n\n" + detail(activity, inView, inUse, minutes))
            .setPositiveButton(R.string.fix_help_settings) { _, _ ->
                try {
                    activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    // No such settings page on this phone: the words above have to do.
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    companion object {
        private const val TICK_MS = 5_000L
        private const val SLOW_MS = 20_000L

        /** True when a first fix has been waited for long enough to say so. */
        @JvmStatic
        fun isSlow(waitedMs: Long): Boolean = waitedMs >= SLOW_MS

        /** The line on the map. [inView] is -1 when the phone gives no satellite report. */
        @JvmStatic
        fun lineText(context: Context, inView: Int, inUse: Int): String =
            if (inView < 0) context.getString(R.string.fix_help_line)
            else context.getString(R.string.fix_help_line_satellites, inView, inUse)

        /** The last lines of the explanation: satellites, and how old the last position is. */
        @JvmStatic
        fun detail(context: Context, inView: Int, inUse: Int, lastMinutes: Int?): String {
            val seen = if (inView < 0) "" else context.getString(R.string.fix_help_satellites, inView, inUse) + "\n"
            val last = when {
                lastMinutes == null -> context.getString(R.string.fix_help_last_none)
                lastMinutes < 1 -> context.getString(R.string.fix_help_last_now)
                lastMinutes < 120 -> context.getString(R.string.fix_help_last_minutes, lastMinutes)
                else -> context.getString(R.string.fix_help_last_hours, lastMinutes / 60)
            }
            return seen + last
        }
    }
}
