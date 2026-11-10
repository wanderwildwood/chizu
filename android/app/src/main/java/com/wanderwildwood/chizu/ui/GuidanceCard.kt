package com.wanderwildwood.chizu.ui

import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.DisplayMetrics
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.organicmaps.R
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.routing.CarDirection
import app.organicmaps.sdk.routing.PedestrianTurnDirection
import app.organicmaps.sdk.routing.RouteStepInfo
import app.organicmaps.sdk.routing.RoutingInfo
import app.organicmaps.sdk.util.StringUtils
import app.organicmaps.sdk.util.Utils
import app.organicmaps.sdk.widget.roadshield.RoadShieldUtils
import app.organicmaps.widget.LanesView
import app.organicmaps.widget.SpeedLimitView

/*
 * The guidance card. While a route is followed it covers the map: the next turn in large type,
 * the two turns after it, the speed limit and the speed, and a Map button. An E Ink panel cannot
 * keep up with a map that moves under the car, so the map is asked for (Map, and Turns to come
 * back) and, while it shows, takes a new position once every MAP_STEP_SEC seconds.
 *
 * Nothing here is set twice with the same value: on E Ink every setText is a redraw.
 */
class GuidanceCard(private val card: View, private val turnsButton: View) {
    private val turn: ImageView = card.findViewById(R.id.card_turn)
    private val exit: TextView = card.findViewById(R.id.card_exit)
    private val distance: TextView = card.findViewById(R.id.card_distance)
    private val street: TextView = card.findViewById(R.id.card_street)
    private val lanes: LanesView = card.findViewById(R.id.card_lanes)
    private val thenRows: List<View> =
        listOf(card.findViewById(R.id.card_then_1), card.findViewById(R.id.card_then_2))
    private val speedLimit: SpeedLimitView = card.findViewById(R.id.card_speed_limit)
    private val speed: TextView = card.findViewById(R.id.card_speed)

    private var following = false
    private var mapAsked = false
    private var footHeight = 0
    private var topInset = 0

    private var turnRes = 0
    private var lanesKey: String? = null
    private var steps: Array<RouteStepInfo>? = null
    private var stepsRouteId = -1L
    private var thenKey: String? = null

    init {
        card.findViewById<View>(R.id.card_map).setOnClickListener { showMap(true) }
        turnsButton.setOnClickListener { showMap(false) }
        ViewCompat.setOnApplyWindowInsetsListener(card) { _, insets ->
            topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            pad()
            insets
        }
        apply()
    }

    /** Guidance started or ended. Each drive starts on the card. */
    fun setFollowing(on: Boolean) {
        if (following == on) return
        following = on
        mapAsked = false
        steps = null
        apply()
    }

    /** The height of the foot (arrival, minutes, miles) the card must stay clear of. */
    fun setFootHeight(px: Int) {
        if (footHeight == px) return
        footHeight = px
        pad()
    }

    private fun showMap(on: Boolean) {
        mapAsked = on
        apply()
    }

    private fun apply() {
        card.visibility = if (following && !mapAsked) View.VISIBLE else View.GONE
        turnsButton.visibility = if (following && mapAsked) View.VISIBLE else View.GONE
        // Under the card the map keeps taking positions at the same step: it has to know where the
        // car is to stay in its follow mode, and it is right the moment Map is pressed.
        Framework.nativeSetGuidanceMapStep(if (following) MAP_STEP_SEC else 0)
    }

    private fun pad() = card.setPadding(0, topInset, 0, footHeight)

    fun update(info: RoutingInfo, speedMps: Float?, showSpeedLimit: Boolean) {
        if (!following || mapAsked) return
        val pedestrian = Router.get() == Router.Pedestrian

        val res = if (pedestrian) info.pedestrianTurnDirection.turnRes else info.carDirection.turnRes
        if (res != turnRes) {
            turnRes = res
            // The turn signs are bitmaps; take the largest one, the card draws it four times the size.
            turn.setImageDrawable(
                ResourcesCompat.getDrawableForDensity(card.resources, res, DisplayMetrics.DENSITY_XXXHIGH, card.context.theme)
            )
        }
        val roundabout = !pedestrian && CarDirection.isRoundAbout(info.carDirection)
        exit.setTextIfNew(if (roundabout) info.exitNum.toString() else "")
        exit.visibility = if (roundabout) View.VISIBLE else View.GONE

        val number = info.distToTurn.mDistanceStr
        val whole = info.distToTurn.toString(card.context)
        if (distance.text.toString() != whole) {
            distance.text = SpannableString(whole).apply {
                setSpan(RelativeSizeSpan(0.5f), number.length, whole.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        val road = RoadShieldUtils.composeInstruction(info, street.textSize)
        if (street.text.toString() != road.toString()) street.text = road

        val key = if (pedestrian) null else info.lanes?.joinToString("|") { it.toString() }
        if (key != lanesKey) {
            lanesKey = key
            lanes.setLanes(if (key == null) null else info.lanes)
        }

        updateThen(info)

        val limit = if (showSpeedLimit) StringUtils.nativeFormatSpeed(info.speedLimitMps) else -1
        if (speedMps == null) {
            speedLimit.setSpeedLimitIfNew(limit, false)
            speed.setTextIfNew("")
        } else {
            val now = StringUtils.nativeFormatSpeed(speedMps.toDouble())
            speedLimit.setSpeedLimitIfNew(limit, limit in 1 until now)
            val unit = StringUtils.nativeFormatSpeedAndUnits(speedMps.toDouble()).second
            speed.setTextIfNew(card.context.getString(R.string.guidance_speed, inSteps(now), unit))
        }
    }

    /** The two turns after the next one, each with the distance from the turn before it. */
    private fun updateThen(info: RoutingInfo) {
        if (steps == null || stepsRouteId != info.routeId) {
            steps = Framework.nativeGetRouteSteps(Utils.getLanguageCode())
            stepsRouteId = info.routeId
        }
        val after = turnsAfter(steps?.map { it.sequence } ?: emptyList(), info.currentStep, thenRows.size)
        val key = "${info.routeId}:$after"
        if (key == thenKey) return
        thenKey = key
        thenRows.forEachIndexed { i, row ->
            val step = after.getOrNull(i)?.let { steps!![it] }
            row.visibility = if (step == null) View.INVISIBLE else View.VISIBLE
            if (step == null) return@forEachIndexed
            row.findViewById<ImageView>(R.id.then_turn).setImageResource(sign(step))
            row.findViewById<TextView>(R.id.then_distance).text = step.formattedDistance.toString(card.context)
            row.findViewById<TextView>(R.id.then_street).text = rowText(
                step.toStreetName, step.toRef, step.junctionRef, step.destinationRef, step.destination,
                card.context.getString(R.string.guidance_exit), step.textualInstruction,
            )
        }
    }

    private var limitShown = Int.MIN_VALUE
    private var limitAlert = false

    private fun SpeedLimitView.setSpeedLimitIfNew(limit: Int, alert: Boolean) {
        if (limit == limitShown && alert == limitAlert) return
        limitShown = limit
        limitAlert = alert
        setSpeedLimit(limit, alert)
    }

    private fun TextView.setTextIfNew(value: String) {
        if (text.toString() != value) text = value
    }

    companion object {
        /** How often the map, when asked for, takes a new position. */
        const val MAP_STEP_SEC = 5

        /** The speed is shown in steps of five, so the figure does not change every second. */
        const val SPEED_STEP = 5

        fun inSteps(speed: Int): Int = (speed + SPEED_STEP / 2) / SPEED_STEP * SPEED_STEP

        /** The row's own plain black sign: CoMaps' small bitmaps carry a grey tail that E Ink smears. */
        @JvmStatic
        fun sign(step: RouteStepInfo): Int {
            val car = CarDirection.values().getOrNull(step.turnDirection)
            if (step.turnDirection > 0 && car != null) return when (car) {
                CarDirection.TURN_RIGHT -> R.drawable.ic_card_right
                CarDirection.TURN_SHARP_RIGHT -> R.drawable.ic_card_right_sharp
                CarDirection.TURN_SLIGHT_RIGHT -> R.drawable.ic_card_right_slight
                CarDirection.TURN_LEFT -> R.drawable.ic_card_left
                CarDirection.TURN_SHARP_LEFT -> R.drawable.ic_card_left_sharp
                CarDirection.TURN_SLIGHT_LEFT -> R.drawable.ic_card_left_slight
                CarDirection.U_TURN_LEFT -> R.drawable.ic_card_uleft
                CarDirection.U_TURN_RIGHT -> R.drawable.ic_card_uright
                CarDirection.ENTER_ROUND_ABOUT, CarDirection.LEAVE_ROUND_ABOUT, CarDirection.STAY_ON_ROUND_ABOUT ->
                    R.drawable.ic_card_round
                CarDirection.REACHED_YOUR_DESTINATION -> R.drawable.ic_card_finish
                CarDirection.EXIT_HIGHWAY_TO_LEFT -> R.drawable.ic_card_exit_left
                CarDirection.EXIT_HIGHWAY_TO_RIGHT -> R.drawable.ic_card_exit_right
                else -> R.drawable.ic_card_straight
            }
            return when (PedestrianTurnDirection.values().getOrNull(step.pedestrianDirection)) {
                PedestrianTurnDirection.TURN_RIGHT -> R.drawable.ic_card_right
                PedestrianTurnDirection.TURN_LEFT -> R.drawable.ic_card_left
                PedestrianTurnDirection.REACHED_YOUR_DESTINATION -> R.drawable.ic_card_finish
                else -> R.drawable.ic_card_straight
            }
        }

        /**
         * What a "then" row says. A street has its name ("East 20th Street", or its number when it
         * has no name). A highway exit says its number and where it leads: "Exit 384, CA 32 Chico".
         * [exitWord] is "Exit"; [spoken] is the router's own sentence, the last resort.
         */
        fun rowText(
            name: String?, ref: String?, junction: String?, destinationRef: String?, destination: String?,
            exitWord: String, spoken: String?,
        ): String {
            // "San Jose; San Francisco International Airport", "CA 99;99": the first is enough for a row.
            fun String?.known() = this?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
            val place = destination.known() ?: name.known()
            val exit = junction.known()
            if (exit == null && destination.known() == null)
                return name.known() ?: ref.known() ?: destinationRef.known() ?: spoken.known() ?: exitWord
            val number = destinationRef.known() ?: ref.known()
            val leads = listOfNotNull(number, place.takeIf { it != number }).joinToString(" ")
            val head = if (exit == null) exitWord else "$exitWord $exit"
            return if (leads.isEmpty()) head else "$head, $leads"
        }

        /**
         * Positions in the step list of up to [count] turns that follow the one being approached.
         * [sequences] are the steps' sequence numbers in route order, [current] the sequence of the
         * turn being approached (0 = none).
         */
        fun turnsAfter(sequences: List<Int>, current: Int, count: Int): List<Int> {
            val at = sequences.indexOf(current)
            if (current <= 0 || at < 0) return emptyList()
            return (at + 1..at + count).filter { it < sequences.size }
        }
    }
}
