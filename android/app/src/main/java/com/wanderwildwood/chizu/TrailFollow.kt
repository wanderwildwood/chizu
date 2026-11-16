package com.wanderwildwood.chizu

import android.app.Activity
import android.content.Context
import android.location.Location
import android.view.View
import android.widget.TextView
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.bookmarks.data.BookmarkManager
import app.organicmaps.sdk.location.LocationListener
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.util.StringUtils
import com.wanderwildwood.chizu.ui.DarkAlertDialogBuilder
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Walking an imported trail. No route is made and no map data is asked: the track's own line is
 * what is followed. A line on the map says how far it is to the trail's end, and says so when the
 * walker has strayed from it; a press on the line turns the walk around or stops it.
 */
class TrailFollow(private val activity: Activity, private val line: TextView) {
    private val listener = LocationListener { location -> onLocation(location) }
    private var listening = false

    // Tracks are read from their files after the app starts: a remembered trail waits for them.
    private var waiting = false
    private val tracksRead = object : BookmarkManager.BookmarksLoadingListener {
        // Not from inside the call: the listener list is being walked.
        override fun onBookmarksLoadingFinished() {
            line.post { if (waiting) revive() }
        }
    }

    init {
        line.setOnClickListener { showChoices() }
    }

    fun onResume() {
        if (!listening) {
            MwmApplication.from(activity).locationHelper.addListener(listener)
            listening = true
        }
        changed = { refresh() }
        revive()
        refresh()
    }

    fun onPause() {
        if (listening) {
            MwmApplication.from(activity).locationHelper.removeListener(listener)
            listening = false
        }
        await(false)
        changed = null
    }

    private fun await(on: Boolean) {
        if (on == waiting) return
        waiting = on
        if (on) BookmarkManager.INSTANCE.addLoadingListener(tracksRead)
        else BookmarkManager.INSTANCE.removeLoadingListener(tracksRead)
    }

    /**
     * Picks a remembered trail back up after the app was closed, and lets go of one whose track is
     * gone. A track is known again by its line, since its number changes from one start to the next.
     */
    private fun revive() {
        val saved = saved(activity) ?: return
        if (BookmarkManager.INSTANCE.isAsyncBookmarksLoadingInProgress) {
            await(true)
            return
        }
        await(false)
        if (current != null && BookmarkManager.nativeGetTrackPoints(trackId).isNotEmpty()) return
        val manager = BookmarkManager.INSTANCE
        val ids = manager.categories.flatMap { list ->
            (0 until list.tracksCount).map { manager.getTrackIdByPosition(list.id, it) }
        }
        val found = ids.firstOrNull { saved.isLineOf(BookmarkManager.nativeGetTrackPoints(it)) }
        if (found == null) stop(activity)
        else resume(found, manager.getTrack(found).name, BookmarkManager.nativeGetTrackPoints(found), saved)
        refresh()
    }

    private fun refresh() {
        if (current != null && spot == null) MwmApplication.from(activity).locationHelper.savedLocation?.let { onLocation(it) }
        show()
    }

    private fun show() {
        val trail = current
        // A route being planned or driven has the top of the map to itself.
        if (trail == null || RoutingController.get().isPlanning || RoutingController.get().isNavigating) {
            line.visibility = View.GONE
            return
        }
        line.text = lineText(activity, spot, trail.length) { format(it) }
        line.visibility = View.VISIBLE
    }

    private fun onLocation(location: Location) {
        val trail = current ?: return
        spot = trail.locate(location.latitude, location.longitude, spot?.along)
            .let { it.copy(off = isOff(it.away, if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0)) }
        show()
    }

    private fun showChoices() {
        val trail = current ?: return
        DarkAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(name)
            .setMessage(detail(activity, spot, trail.length) { format(it) })
            // Stacked, they read from the top: turn around, stop, close.
            .setPositiveButton(R.string.trail_turn_around) { _, _ -> turnAround(activity) }
            .setNegativeButton(R.string.trail_stop) { _, _ -> stop(activity) }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    private fun format(meters: Double): String = StringUtils.nativeFormatDistance(meters).toString(activity)

    /** Where the walker is, measured against the trail. */
    data class Spot(
        /** Meters walked along the trail from its start to the point of it nearest the walker. */
        val along: Double,
        /** Meters from the walker to that point. */
        val away: Double,
        val off: Boolean = false,
    )

    /**
     * What is kept of a followed trail while the app is closed: which way it is walked, and enough
     * of its line, as the track has it, to know the track again.
     */
    class Saved(val reversed: Boolean, val points: Int, private val samples: DoubleArray) {
        fun line(): String = "${if (reversed) 1 else 0} $points " + samples.joinToString(" ")

        fun turned(nowReversed: Boolean) = Saved(nowReversed, points, samples)

        /**
         * True when [coordinates], a track's latitude and longitude pairs, are this trail's line: as
         * many points, and the few kept ones where they were. A track file keeps a little less of
         * each number than the track had when it came in, so "where they were" is to a few meters.
         */
        fun isLineOf(coordinates: DoubleArray): Boolean {
            if (points < 2 || coordinates.size / 2 != points) return false
            val there = sample(coordinates)
            return there.indices.all { Math.abs(there[it] - samples[it]) <= SAME_POINT_DEGREES }
        }

        companion object {
            private const val SAMPLES = 5
            private const val SAME_POINT_DEGREES = 0.00003

            @JvmStatic
            fun of(coordinates: DoubleArray, reversed: Boolean) = Saved(reversed, coordinates.size / 2, sample(coordinates))

            /** Null for anything that is not a line written by [line]. */
            @JvmStatic
            fun parse(line: String?): Saved? {
                val parts = line?.split(' ') ?: return null
                if (parts.size < 3 || (parts[0] != "0" && parts[0] != "1")) return null
                val points = parts[1].toIntOrNull() ?: return null
                if (points < 2 || parts.size != 2 + 2 * sampled(points).size) return null
                val samples = parts.drop(2).map { it.toDoubleOrNull() ?: return null }
                return Saved(parts[0] == "1", points, samples.toDoubleArray())
            }

            /** Which points are kept: the first, the last and a few spread between them. */
            private fun sampled(points: Int): List<Int> =
                (0 until SAMPLES).map { it * (points - 1) / (SAMPLES - 1) }.distinct()

            private fun sample(coordinates: DoubleArray): DoubleArray {
                val points = coordinates.size / 2
                if (points < 2) return DoubleArray(0)
                val kept = sampled(points)
                return DoubleArray(kept.size * 2) { coordinates[2 * kept[it / 2] + it % 2] }
            }
        }
    }

    /** A trail as a line of points, each a latitude and a longitude, in the order walked. */
    class Trail(private val lats: DoubleArray, private val lons: DoubleArray) {
        /** Meters from the start to each point. */
        private val reach = DoubleArray(lats.size)

        init {
            require(lats.size == lons.size && lats.size >= 2)
            for (i in 1 until lats.size) reach[i] = reach[i - 1] + meters(lats[i - 1], lons[i - 1], lats[i], lons[i])
        }

        val length: Double get() = reach[reach.size - 1]

        fun reversed(): Trail = Trail(lats.reversedArray(), lons.reversedArray())

        /**
         * The point of the trail the walker is at. [last] is how far along the walker was a moment
         * ago, if known. A trail can pass the same place twice (out and back, a figure of eight), so
         * the nearest point alone is not enough: the part just ahead of [last] is preferred, and
         * when the walker is nowhere near that part, the nearest place further on.
         */
        fun locate(lat: Double, lon: Double, last: Double?): Spot {
            val n = lats.size - 1
            val along = DoubleArray(n)
            val away = DoubleArray(n)
            var nearest = Double.MAX_VALUE
            val east = METERS_PER_DEGREE * cos(Math.toRadians(lat))
            for (i in 0 until n) {
                // Flat meters around the walker: a trail's pieces are short enough for that.
                val ax = (lons[i] - lon) * east
                val ay = (lats[i] - lat) * METERS_PER_DEGREE
                val bx = (lons[i + 1] - lon) * east
                val by = (lats[i + 1] - lat) * METERS_PER_DEGREE
                val dx = bx - ax
                val dy = by - ay
                val square = dx * dx + dy * dy
                val t = if (square == 0.0) 0.0 else (-(ax * dx + ay * dy) / square).coerceIn(0.0, 1.0)
                away[i] = hypot(ax + t * dx, ay + t * dy)
                along[i] = reach[i] + t * (reach[i + 1] - reach[i])
                if (away[i] < nearest) nearest = away[i]
            }
            val near = nearest + SAME_PLACE_M
            var pick = -1
            if (last != null) {
                // Just ahead of (or a little behind) where the walker was: the nearest piece there.
                for (i in 0 until n)
                    if (away[i] <= near && along[i] >= last - BEHIND_M && along[i] <= last + AHEAD_M &&
                        (pick < 0 || away[i] < away[pick])
                    ) pick = i
                // Not there: the walker cut across or turned back early. The first place further on.
                if (pick < 0) pick = (0 until n).firstOrNull { away[it] <= near && along[it] >= last } ?: -1
            }
            // A first look, or the walker went back toward the start: the earliest of the nearest.
            if (pick < 0) pick = (0 until n).first { away[it] <= nearest + FIRST_LOOK_M }
            return Spot(along[pick], away[pick])
        }
    }

    companion object {
        /** Farther from the trail than this is off it. */
        const val OFF_TRAIL_M = 50.0

        /** Closer to the end than this is there. */
        const val END_M = 30.0

        // Two passes of the trail closer together than this are the same place to a phone.
        private const val SAME_PLACE_M = 15.0
        private const val FIRST_LOOK_M = 5.0
        private const val BEHIND_M = 50.0
        private const val AHEAD_M = 200.0
        private const val METERS_PER_DEGREE = 111_194.9

        private var current: Trail? = null
        private var name: String = ""
        private var spot: Spot? = null
        private var trackId: Long = -1
        private var reversed = false

        private const val PREFS = "atlas_trail"
        private const val KEY_FOLLOWED = "followed"

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun saved(context: Context): Saved? = Saved.parse(prefs(context).getString(KEY_FOLLOWED, null))

        // The map screen's line, while that screen is up.
        private var changed: (() -> Unit)? = null

        @JvmStatic
        val isFollowing: Boolean get() = current != null

        /**
         * Starts following the track; false when it has no line to follow. [coordinates] holds a
         * latitude and a longitude for each point.
         */
        @JvmStatic
        fun start(trackName: String, coordinates: DoubleArray): Boolean {
            val count = coordinates.size / 2
            if (count < 2) return false
            current = Trail(DoubleArray(count) { coordinates[2 * it] }, DoubleArray(count) { coordinates[2 * it + 1] })
            name = trackName
            spot = null
            trackId = -1
            reversed = false
            changed?.invoke()
            return true
        }

        /** Starts following the track with this id, as the place page's button does, and remembers it. */
        @JvmStatic
        fun start(context: Context, id: Long, trackName: String): Boolean {
            val coordinates = BookmarkManager.nativeGetTrackPoints(id)
            if (!start(trackName, coordinates)) return false
            trackId = id
            prefs(context).edit().putString(KEY_FOLLOWED, Saved.of(coordinates, false).line()).apply()
            return true
        }

        /** Follows a remembered trail again, the way it was being walked. */
        private fun resume(id: Long, trackName: String, coordinates: DoubleArray, saved: Saved) {
            if (!start(trackName, coordinates)) return
            trackId = id
            if (saved.reversed) turnAround()
        }

        @JvmStatic
        fun stop() {
            current = null
            spot = null
            trackId = -1
            reversed = false
            changed?.invoke()
        }

        /** Stops, and forgets the trail, so it is not picked up again when the app next starts. */
        @JvmStatic
        fun stop(context: Context) {
            prefs(context).edit().remove(KEY_FOLLOWED).apply()
            stop()
        }

        /** A track was deleted: if it is the one followed, following ends with it. */
        @JvmStatic
        fun trackDeleted(context: Context, id: Long) {
            if (current != null && id == trackId) stop(context)
        }

        /** The trail's end becomes its start; the place on it stays the same. */
        @JvmStatic
        fun turnAround() {
            val trail = current ?: return
            current = trail.reversed()
            reversed = !reversed
            spot = spot?.let { it.copy(along = trail.length - it.along) }
            changed?.invoke()
        }

        /** Turns around, and remembers which way the trail is now walked. */
        @JvmStatic
        fun turnAround(context: Context) {
            turnAround()
            saved(context)?.let { prefs(context).edit().putString(KEY_FOLLOWED, it.turned(reversed).line()).apply() }
        }

        @JvmStatic
        val isReversed: Boolean get() = reversed

        /** Off the trail: farther from it than [OFF_TRAIL_M], and than the position is uncertain. */
        @JvmStatic
        fun isOff(away: Double, accuracy: Double): Boolean = away > maxOf(OFF_TRAIL_M, accuracy)

        @JvmStatic
        fun left(spot: Spot, length: Double): Double = (length - spot.along).coerceAtLeast(0.0)

        @JvmStatic
        fun isAtEnd(spot: Spot, length: Double): Boolean = !spot.off && left(spot, length) <= END_M

        /** The line on the map. */
        @JvmStatic
        fun lineText(context: Context, spot: Spot?, length: Double, format: (Double) -> String): String = when {
            spot == null -> context.getString(R.string.trail_line_waiting)
            spot.off -> context.getString(R.string.trail_line_off, format(spot.away), format(left(spot, length)))
            isAtEnd(spot, length) -> context.getString(R.string.trail_line_end)
            else -> context.getString(R.string.trail_line_left, format(left(spot, length)))
        }

        /** The words under the trail's name when the line is pressed. */
        @JvmStatic
        fun detail(context: Context, spot: Spot?, length: Double, format: (Double) -> String): String {
            if (spot == null) return context.getString(R.string.trail_detail_waiting, format(length))
            val walked = context.getString(R.string.trail_detail, format(left(spot, length)), format(spot.along))
            return if (spot.off) context.getString(R.string.trail_detail_off, format(spot.away)) + "\n\n" + walked
            else walked
        }

        private fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val east = METERS_PER_DEGREE * cos(Math.toRadians((lat1 + lat2) / 2))
            return hypot((lon2 - lon1) * east, (lat2 - lat1) * METERS_PER_DEGREE)
        }
    }
}
