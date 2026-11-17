package com.wanderwildwood.chizu.ui

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import app.organicmaps.R
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.routing.RoutingOptions
import app.organicmaps.sdk.settings.RoadType
import app.organicmaps.util.Utils

/*
 * A second route to choose from while a drive is planned: the fastest one, or one that keeps off
 * the highways. The router works out one route at a time, so each is computed when it is tapped;
 * once seen, a choice keeps its minutes and miles beside its name for as long as the trip is the
 * same.
 *
 * "No highways" is the router's own "avoid motorways" option. Chosen here, either way, it belongs
 * to this trip: when the planning or the drive ends the option is back as Settings had it.
 */
class RouteChoice(private val row: View) {
    private val fastest: TextView = row.findViewById(R.id.route_choice_fastest)
    private val noHighways: TextView = row.findViewById(R.id.route_choice_no_highways)
    private var trip: String? = null
    private val seen = HashMap<Boolean, String>()
    private var shown: String? = null

    init {
        fastest.setOnClickListener { choose(false) }
        noHighways.setOnClickListener { choose(true) }
    }

    private fun choose(avoid: Boolean) {
        if (avoid == avoiding()) return
        rememberBeforeTrip(row.context, avoiding())
        if (avoid) RoutingOptions.addOption(RoadType.Motorway, Router.Vehicle)
        else RoutingOptions.removeOption(RoadType.Motorway, Router.Vehicle)
        update()
        row.post { RoutingController.get().rebuildLastRoute() }
    }

    /** Called whenever the planning panel is brought up to date. */
    fun update() {
        val routing = RoutingController.get()
        val start = routing.startPoint
        val end = routing.endPoint
        val on = routing.isPlanning && routing.isVehicleRouterType && start != null && end != null
        val visibility = if (on) View.VISIBLE else View.GONE
        if (row.visibility != visibility) row.visibility = visibility
        if (!on) return

        val key = "${start!!.lat},${start.lon}>${end!!.lat},${end.lon}"
        if (key != trip) {
            trip = key
            seen.clear()
        }
        val avoid = avoiding()
        if (routing.isBuilt) routing.cachedRoutingInfo?.let { info ->
            val time = Utils.formatRoutingTime(row.context, info.totalTimeInSeconds, R.dimen.text_size_routing_number)
            seen[avoid] = row.context.getString(
                R.string.route_choice_figures, time.toString(), info.distToTarget.toString(row.context))
        }
        val state = "$avoid|${seen[false]}|${seen[true]}"
        if (state == shown) return
        shown = state
        fastest.isSelected = !avoid
        noHighways.isSelected = avoid
        fastest.text = label(R.string.route_choice_fastest, seen[false])
        noHighways.text = label(R.string.route_choice_no_highways, seen[true])
    }

    private fun label(name: Int, figures: String?): CharSequence {
        val title = row.context.getString(name)
        return SpannableString(if (figures == null) title else "$title\n$figures").apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun avoiding() = RoutingOptions.hasOption(RoadType.Motorway, Router.Vehicle)

    companion object {
        private const val PREFS = "atlas_go"
        /** Written by versions before 0.1.14: "No highways" was chosen for the trip. */
        private const val KEY = "no_highways_this_trip"
        /** Whether motorways were avoided (Settings) before the first choice made for this trip. */
        private const val KEY_BEFORE = "avoid_motorways_before_trip"

        /** Only the first choice of a trip records it, so the trip ends where Settings had it. */
        private fun rememberBeforeTrip(context: Context, avoiding: Boolean) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.contains(KEY_BEFORE) || prefs.getBoolean(KEY, false)) return
            prefs.edit().putBoolean(KEY_BEFORE, avoiding).apply()
        }

        /**
         * The planning or the drive is over: the choice made for that trip goes with it, and
         * the avoid-motorways setting is back as it was in Settings, whichever way it went.
         */
        @JvmStatic
        fun tripOver(context: Context) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val before = when {
                prefs.contains(KEY_BEFORE) -> prefs.getBoolean(KEY_BEFORE, false)
                prefs.getBoolean(KEY, false) -> false
                else -> return
            }
            if (before) RoutingOptions.addOption(RoadType.Motorway, Router.Vehicle)
            else RoutingOptions.removeOption(RoadType.Motorway, Router.Vehicle)
            prefs.edit().remove(KEY).remove(KEY_BEFORE).apply()
        }
    }
}
