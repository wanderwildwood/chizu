package com.wanderwildwood.chizu

import android.app.Activity
import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.Router
import app.organicmaps.sdk.bookmarks.data.BookmarkManager
import app.organicmaps.sdk.bookmarks.data.FeatureId
import app.organicmaps.sdk.bookmarks.data.MapObject
import app.organicmaps.sdk.location.LocationListener
import app.organicmaps.sdk.routing.RoutingController
import app.organicmaps.sdk.util.StringUtils
import com.wanderwildwood.chizu.ui.DarkAlertDialogBuilder
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Walking an imported trail, whole or up to a spot on it. No route is made along it and no map
 * data is asked: the track's own line is what is followed. A line on the map says how far it is to
 * the end (or to the chosen stop), and says so when the walker has strayed from it; a press on the
 * line turns the walk around or stops it. Away from the trail, CoMaps' own walking route can take
 * the walker to the nearest point of it, and following picks up from there.
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
        if (followed != null && BookmarkManager.nativeGetTrackPoints(trackId).isNotEmpty()) return
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
        if (followed != null && spot == null) MwmApplication.from(activity).locationHelper.savedLocation?.let { onLocation(it) }
        show()
    }

    private fun show() {
        // A route being planned or driven has the top of the map to itself.
        if (followed == null || RoutingController.get().isPlanning || RoutingController.get().isNavigating) {
            line.visibility = View.GONE
            return
        }
        line.text = lineText(activity, spot, current?.length ?: 0.0, isStretch) { format(it) }
        line.visibility = View.VISIBLE
    }

    private fun onLocation(location: Location) {
        val track = followed ?: return
        // A stretch begins where the walker meets the trail, so it waits for the first position:
        // a fresh, close one. The position the phone last had, replayed at once, may be old or rough.
        if (current == null) {
            val stopAt = stop ?: return
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (!canBegin(age, if (location.hasAccuracy()) location.accuracy.toDouble() else null)) return
            begin(activity, track.locate(location.latitude, location.longitude, null).along, stopAt)
        }
        val trail = current ?: return
        spot = trail.locate(location.latitude, location.longitude, spot?.along)
            .let { it.copy(off = isOff(it.away, if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0)) }
        show()
        if (offerWalk) {
            offerWalk = false
            // Not from inside the call: it can come from the place page's button.
            if (spot?.off == true) line.post { offerWalkTo() }
        }
    }

    private fun showChoices() {
        val trail = current
        val here = spot
        val dialog = DarkAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(name)
            .setMessage(detail(activity, here, trail?.length ?: followed?.length ?: 0.0, isStretch) { format(it) })
        // Stacked, they read from the top: walk to the trail or turn around, stop, close. Off the
        // trail, getting back to it comes first; a stretch not yet begun has no way round.
        if (here?.off == true) dialog.setPositiveButton(R.string.trail_walk_to) { _, _ -> walkTo() }
        else if (trail != null || !isStretch) dialog.setPositiveButton(R.string.trail_turn_around) { _, _ -> turnAround(activity) }
        dialog.setNegativeButton(R.string.trail_stop) { _, _ -> stop(activity) }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    /** Asked once, at the first position after following began, when that is away from the trail. */
    private fun offerWalkTo() {
        val here = spot ?: return
        if (!here.off || RoutingController.get().isPlanning || RoutingController.get().isNavigating) return
        DarkAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(name)
            .setMessage(activity.getString(R.string.trail_walk_to_offer, format(here.away)))
            .setPositiveButton(R.string.trail_walk_to) { _, _ -> walkTo() }
            .setNeutralButton(R.string.close, null)
            .show()
    }

    /**
     * CoMaps' walking route from where the walker is to the point of the trail nearest them. The
     * trail's line comes back once the route is done or closed, and following goes on from there.
     */
    private fun walkTo() {
        val trail = current ?: return
        val here = spot ?: return
        val (lat, lon) = trail.pointAt(here.along)
        val from = MwmApplication.from(activity).locationHelper.myPosition
        val to = MapObject.createMapObject(FeatureId.EMPTY, MapObject.API_POINT, name, "", lat, lon)
        RoutingController.get().prepare(from, to, Router.Pedestrian)
        show()
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
     * What is kept of a followed trail while the app is closed: which way it is walked, or which
     * stretch of it, and enough of its line, as the track has it, to know the track again.
     * [begin] and [stop] are meters along the track from its own start; [stop] is null for the
     * whole trail, [begin] is null for a stretch whose walker had not yet been placed on it.
     */
    class Saved(
        val reversed: Boolean,
        val points: Int,
        private val samples: DoubleArray,
        val begin: Double? = null,
        val stop: Double? = null,
    ) {
        fun line(): String = "${if (reversed) 1 else 0} $points " + samples.joinToString(" ") +
            (stop?.let { " $PART ${begin?.toString() ?: NOT_YET} $it" } ?: "")

        fun turned(nowReversed: Boolean) = Saved(nowReversed, points, samples)

        fun stretch(from: Double?, to: Double) = Saved(from != null && from > to, points, samples, from, to)

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
            private const val PART = "part"
            private const val NOT_YET = "-"
            private const val SAME_POINT_DEGREES = 0.00003

            @JvmStatic
            fun of(coordinates: DoubleArray, reversed: Boolean) = Saved(reversed, coordinates.size / 2, sample(coordinates))

            /** Null for anything that is not a line written by [line]. */
            @JvmStatic
            fun parse(line: String?): Saved? {
                val parts = line?.split(' ') ?: return null
                if (parts.size < 3 || (parts[0] != "0" && parts[0] != "1")) return null
                val points = parts[1].toIntOrNull() ?: return null
                if (points < 2) return null
                val end = 2 + 2 * sampled(points).size
                val samples = parts.subList(2, minOf(end, parts.size)).map { it.toDoubleOrNull() ?: return null }
                if (parts.size == end) return Saved(parts[0] == "1", points, samples.toDoubleArray())
                // A stretch: "part", where it began (or "-"), where it stops.
                if (parts.size != end + 3 || parts[end] != PART) return null
                val begin = if (parts[end + 1] == NOT_YET) null else (parts[end + 1].toDoubleOrNull() ?: return null)
                val stop = parts[end + 2].toDoubleOrNull() ?: return null
                return Saved(parts[0] == "1", points, samples.toDoubleArray(), begin, stop)
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

        /** The latitude and longitude [along] meters from the start (held to the trail's ends). */
        fun pointAt(along: Double): Pair<Double, Double> {
            val at = along.coerceIn(0.0, length)
            var i = 0
            while (i < reach.size - 2 && reach[i + 1] < at) i++
            val piece = reach[i + 1] - reach[i]
            val t = if (piece == 0.0) 0.0 else ((at - reach[i]) / piece).coerceIn(0.0, 1.0)
            return Pair(lats[i] + t * (lats[i + 1] - lats[i]), lons[i] + t * (lons[i + 1] - lons[i]))
        }

        /**
         * The stretch of the trail from [from] to [to] meters along it, walked in that order: when
         * [from] is the larger, the stretch runs back toward the trail's start.
         */
        fun part(from: Double, to: Double): Trail {
            val low = minOf(from, to).coerceIn(0.0, length)
            val high = maxOf(from, to).coerceIn(0.0, length)
            val la = ArrayList<Double>()
            val lo = ArrayList<Double>()
            fun add(point: Pair<Double, Double>) { la.add(point.first); lo.add(point.second) }
            add(pointAt(low))
            for (i in reach.indices) if (reach[i] > low && reach[i] < high) add(Pair(lats[i], lons[i]))
            add(pointAt(high))
            val stretch = Trail(la.toDoubleArray(), lo.toDoubleArray())
            return if (from > to) stretch.reversed() else stretch
        }

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
                val spot = onPiece(i, lat, lon, east)
                away[i] = spot.away
                along[i] = spot.along
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

        /**
         * The first place, at or after [from] meters along, where the trail runs through [lat],
         * [lon] to within [within] meters; null when it never does.
         */
        fun firstPassAt(lat: Double, lon: Double, from: Double, within: Double): Double? {
            val east = METERS_PER_DEGREE * cos(Math.toRadians(lat))
            for (i in 0 until lats.size - 1) {
                val spot = onPiece(i, lat, lon, east)
                if (spot.along >= from && spot.away <= within) return spot.along
            }
            return null
        }

        /** The point of piece [i] nearest [lat], [lon]: how far along the trail, and how far from it. */
        private fun onPiece(i: Int, lat: Double, lon: Double, east: Double): Spot {
            // Flat meters around the point: a trail's pieces are short enough for that.
            val ax = (lons[i] - lon) * east
            val ay = (lats[i] - lat) * METERS_PER_DEGREE
            val bx = (lons[i + 1] - lon) * east
            val by = (lats[i + 1] - lat) * METERS_PER_DEGREE
            val dx = bx - ax
            val dy = by - ay
            val square = dx * dx + dy * dy
            val t = if (square == 0.0) 0.0 else (-(ax * dx + ay * dy) / square).coerceIn(0.0, 1.0)
            return Spot(reach[i] + t * (reach[i + 1] - reach[i]), hypot(ax + t * dx, ay + t * dy))
        }
    }

    companion object {
        /** Farther from the trail than this is off it. */
        const val OFF_TRAIL_M = 50.0

        /** A position older than this many seconds does not begin a stretch. */
        const val BEGIN_AGE_S = 30L

        /** Closer to the end than this is there. */
        const val END_M = 30.0

        // Two passes of the trail closer together than this are the same place to a phone.
        private const val SAME_PLACE_M = 15.0
        private const val FIRST_LOOK_M = 5.0
        private const val BEHIND_M = 50.0
        private const val AHEAD_M = 200.0
        private const val METERS_PER_DEGREE = 111_194.9

        // A mark lies on the track's line; the map's distance to it may round a little either way.
        private const val MARK_ON_LINE_M = 1.0
        private const val MARK_SLACK_M = 1.0

        /** The followed track, the way it was drawn. */
        private var followed: Trail? = null

        /** The line being walked: the track, turned round or cut to a stretch. Null while a stretch waits to begin. */
        private var current: Trail? = null
        private var name: String = ""
        private var spot: Spot? = null
        private var trackId: Long = -1
        private var reversed = false

        /** Meters along the track where a stretch stops; null when the whole trail is walked. */
        private var stop: Double? = null

        /** Meters along the track where a stretch began: where the walker first met the trail. */
        private var beginAt: Double? = null

        // At the first position after following began, a walker away from the trail is offered a route to it.
        private var offerWalk = false

        private const val PREFS = "atlas_trail"
        private const val KEY_FOLLOWED = "followed"

        private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun saved(context: Context): Saved? = Saved.parse(prefs(context).getString(KEY_FOLLOWED, null))

        // The map screen's line, while that screen is up.
        private var changed: (() -> Unit)? = null

        @JvmStatic
        val isFollowing: Boolean get() = followed != null

        /** True when a stretch of the trail is followed, not the whole of it. */
        @JvmStatic
        val isStretch: Boolean get() = stop != null

        /**
         * Starts following the track; false when it has no line to follow. [coordinates] holds a
         * latitude and a longitude for each point.
         */
        @JvmStatic
        fun start(trackName: String, coordinates: DoubleArray): Boolean {
            if (!load(trackName, coordinates)) return false
            changed?.invoke()
            return true
        }

        /** Takes the track up as the whole trail, without yet telling the map screen. */
        private fun load(trackName: String, coordinates: DoubleArray): Boolean {
            val count = coordinates.size / 2
            if (count < 2) return false
            followed = Trail(DoubleArray(count) { coordinates[2 * it] }, DoubleArray(count) { coordinates[2 * it + 1] })
            current = followed
            name = trackName
            spot = null
            trackId = -1
            reversed = false
            stop = null
            beginAt = null
            offerWalk = true
            return true
        }

        /**
         * Starts following the track up to [stopAt] meters along it. The stretch begins where the
         * walker meets the trail, so it waits for a position, and runs toward the stop either way.
         */
        @JvmStatic
        fun startPart(trackName: String, coordinates: DoubleArray, stopAt: Double): Boolean {
            if (!loadPart(trackName, coordinates, stopAt)) return false
            changed?.invoke()
            return true
        }

        private fun loadPart(trackName: String, coordinates: DoubleArray, stopAt: Double): Boolean {
            if (!load(trackName, coordinates)) return false
            stop = stopAt.coerceIn(0.0, followed!!.length)
            current = null
            return true
        }

        /**
         * Where the spot marked [markedAt] meters along a track, at [lat], [lon], lies on [trail].
         * The map measures the mark line by line and leaves out the jump from the end of one of a
         * track's lines to the start of the next; the trail joins the lines and counts each jump.
         * So the map's distance can only fall short, and the mark's point lies on the line itself:
         * the stop is the first place at or after the map's distance where the trail runs through
         * that point. A trail that passes the same place twice keeps the pass that was marked.
         */
        internal fun stopOnTrail(trail: Trail, markedAt: Double, lat: Double, lon: Double): Double {
            val from = markedAt.coerceIn(0.0, trail.length)
            return trail.firstPassAt(lat, lon, from - MARK_SLACK_M, MARK_ON_LINE_M) ?: from
        }

        /** The stretch begins [from] meters along the track, where the walker met it. */
        private fun begin(context: Context, from: Double, stopAt: Double) {
            val track = followed ?: return
            beginAt = from
            current = track.part(from, stopAt)
            reversed = from > stopAt
            spot = null
            saved(context)?.let { prefs(context).edit().putString(KEY_FOLLOWED, it.stretch(from, stopAt).line()).apply() }
        }

        /** Starts following the track with this id, as the place page's button does, and remembers it. */
        @JvmStatic
        fun start(context: Context, id: Long, trackName: String): Boolean {
            val coordinates = BookmarkManager.nativeGetTrackPoints(id)
            if (!load(trackName, coordinates)) return false
            trackId = id
            prefs(context).edit().putString(KEY_FOLLOWED, Saved.of(coordinates, false).line()).apply()
            changed?.invoke()
            return true
        }

        /**
         * Starts following the track with this id up to the spot marked on it, as the place page's
         * "Walk to here" does, and remembers it. [markedAt] is the map's own distance of the mark
         * along the track, and [lat], [lon] its point.
         */
        @JvmStatic
        fun startPart(context: Context, id: Long, trackName: String, markedAt: Double, lat: Double, lon: Double): Boolean {
            val coordinates = BookmarkManager.nativeGetTrackPoints(id)
            if (!loadPart(trackName, coordinates, markedAt)) return false
            stop = stopOnTrail(followed!!, markedAt, lat, lon)
            trackId = id
            // Kept before the map screen is told: told, it may begin the stretch at once and keep that.
            prefs(context).edit().putString(KEY_FOLLOWED, Saved.of(coordinates, false).stretch(null, stop!!).line()).apply()
            changed?.invoke()
            return true
        }

        /** Follows a remembered trail again, the way it was being walked, or the same stretch of it. */
        private fun resume(id: Long, trackName: String, coordinates: DoubleArray, saved: Saved) {
            val stopAt = saved.stop
            // Loaded quietly: told at once, the map screen would begin the stretch afresh from here.
            if (!(if (stopAt == null) load(trackName, coordinates) else loadPart(trackName, coordinates, stopAt))) return
            trackId = id
            // Picked up again, not begun: no offer of a route.
            offerWalk = false
            val from = saved.begin
            if (stopAt != null && from != null) {
                beginAt = from
                current = followed!!.part(from, stop!!)
                reversed = from > stop!!
            } else if (stopAt == null && saved.reversed) turnAround()
            changed?.invoke()
        }

        @JvmStatic
        fun stop() {
            followed = null
            current = null
            spot = null
            trackId = -1
            reversed = false
            stop = null
            beginAt = null
            offerWalk = false
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
            if (followed != null && id == trackId) stop(context)
        }

        /**
         * The trail's end becomes its start; the place on it stays the same. On a stretch, the walk
         * goes back to where it began.
         */
        @JvmStatic
        fun turnAround() {
            val trail = current ?: return
            current = trail.reversed()
            reversed = !reversed
            spot = spot?.let { it.copy(along = trail.length - it.along) }
            val from = beginAt
            val to = stop
            if (from != null && to != null) {
                beginAt = to
                stop = from
            }
            changed?.invoke()
        }

        /** Turns around, and remembers which way the trail is now walked. */
        @JvmStatic
        fun turnAround(context: Context) {
            turnAround()
            val to = stop
            saved(context)?.let {
                prefs(context).edit().putString(KEY_FOLLOWED, (if (to == null) it.turned(reversed) else it.stretch(beginAt, to)).line()).apply()
            }
        }

        /** Where a stretch stops, in meters along the track; null for the whole trail. */
        @JvmStatic
        val stopAt: Double? get() = stop

        @JvmStatic
        val isReversed: Boolean get() = reversed

        /**
         * Whether a position [ageNanos] old, as sure as [accuracy] meters (null when the phone does
         * not say), is good enough to begin a stretch at: no older than [BEGIN_AGE_S] seconds, and
         * no rougher than [OFF_TRAIL_M].
         */
        @JvmStatic
        fun canBegin(ageNanos: Long, accuracy: Double?): Boolean =
            ageNanos <= BEGIN_AGE_S * 1_000_000_000L && (accuracy == null || accuracy <= OFF_TRAIL_M)

        /** Off the trail: farther from it than [OFF_TRAIL_M], and than the position is uncertain. */
        @JvmStatic
        fun isOff(away: Double, accuracy: Double): Boolean = away > maxOf(OFF_TRAIL_M, accuracy)

        @JvmStatic
        fun left(spot: Spot, length: Double): Double = (length - spot.along).coerceAtLeast(0.0)

        @JvmStatic
        fun isAtEnd(spot: Spot, length: Double): Boolean = !spot.off && left(spot, length) <= END_M

        /** The line on the map. A [stretch] ends at the walker's stop, not at the trail's end. */
        @JvmStatic
        fun lineText(context: Context, spot: Spot?, length: Double, stretch: Boolean, format: (Double) -> String): String = when {
            spot == null -> context.getString(R.string.trail_line_waiting)
            spot.off -> context.getString(if (stretch) R.string.trail_line_off_part else R.string.trail_line_off, format(spot.away), format(left(spot, length)))
            isAtEnd(spot, length) -> context.getString(if (stretch) R.string.trail_line_end_part else R.string.trail_line_end)
            else -> context.getString(if (stretch) R.string.trail_line_left_part else R.string.trail_line_left, format(left(spot, length)))
        }

        /** The words under the trail's name when the line is pressed. */
        @JvmStatic
        fun detail(context: Context, spot: Spot?, length: Double, stretch: Boolean, format: (Double) -> String): String {
            if (spot == null) {
                return if (stretch) context.getString(R.string.trail_detail_waiting_part)
                else context.getString(R.string.trail_detail_waiting, format(length))
            }
            val walked = context.getString(if (stretch) R.string.trail_detail_part else R.string.trail_detail, format(left(spot, length)), format(spot.along))
            return if (spot.off) context.getString(R.string.trail_detail_off, format(spot.away)) + "\n\n" + walked
            else walked
        }

        private fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val east = METERS_PER_DEGREE * cos(Math.toRadians((lat1 + lat2) / 2))
            return hypot((lon2 - lon1) * east, (lat2 - lat1) * METERS_PER_DEGREE)
        }
    }
}
