package com.wanderwildwood.chizu

import android.content.Context
import android.content.Intent
import app.organicmaps.MwmActivity
import app.organicmaps.sdk.bookmarks.data.FeatureId
import app.organicmaps.sdk.bookmarks.data.MapObject
import kotlin.math.abs
import kotlin.math.cos

/*
 * Home and the places last driven to. Both stay on the phone, in the app's own preferences:
 * Home is one place, the recent destinations are the last few a guidance was started to.
 */
object GoPlaces {
    data class Place(val name: String, val lat: Double, val lon: Double) {
        fun toMapObject(): MapObject =
            MapObject.createMapObject(FeatureId.EMPTY, MapObject.API_POINT, name, "", lat, lon)
    }

    const val MAX_RECENT = 12

    /** Two places closer than this are the same destination. */
    const val SAME_PLACE_M = 60.0

    private const val PREFS = "atlas_go"
    private const val KEY_HOME = "home"
    private const val KEY_RECENT = "recent"
    private const val EXTRA_LAT = "atlas.go.lat"
    private const val EXTRA_LON = "atlas.go.lon"
    private const val EXTRA_NAME = "atlas.go.name"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun home(context: Context): Place? = prefs(context).getString(KEY_HOME, null)?.let(::parse)

    @JvmStatic
    fun setHome(context: Context, place: MapObject, fallbackName: String): Place {
        val home = Place(nameOf(place, fallbackName), place.lat, place.lon)
        val recent = recent(context).filterNot { near(it, home) }
        prefs(context).edit().putString(KEY_HOME, line(home)).putString(KEY_RECENT, lines(recent)).apply()
        return home
    }

    @JvmStatic
    fun forgetHome(context: Context) = prefs(context).edit().remove(KEY_HOME).apply()

    @JvmStatic
    fun recent(context: Context): List<Place> =
        prefs(context).getString(KEY_RECENT, null)?.let(::parseAll) ?: emptyList()

    @JvmStatic
    fun clearRecent(context: Context) = prefs(context).edit().remove(KEY_RECENT).apply()

    /** A guidance was started to [end]: it goes to the top of the recent destinations. Home is not listed. */
    @JvmStatic
    fun droveTo(context: Context, end: MapObject?, fallbackName: String) {
        if (end == null || end.isMyPosition) return
        val place = Place(nameOf(end, fallbackName), end.lat, end.lon)
        if (home(context)?.let { near(it, place) } == true) return
        prefs(context).edit().putString(KEY_RECENT, lines(added(recent(context), place))).apply()
    }

    /** From any screen: back to the map with a route from here to [place]. */
    @JvmStatic
    fun go(context: Context, place: Place) {
        context.startActivity(
            Intent(context, MwmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_LAT, place.lat).putExtra(EXTRA_LON, place.lon).putExtra(EXTRA_NAME, place.name)
        )
    }

    /** The map's side of [go]. True when the intent asked for a route. */
    @JvmStatic
    fun routeFrom(activity: MwmActivity, intent: Intent): Boolean {
        if (!intent.hasExtra(EXTRA_LAT) || !intent.hasExtra(EXTRA_LON)) return false
        val place = Place(
            intent.getStringExtra(EXTRA_NAME).orEmpty(),
            intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LON, 0.0),
        )
        activity.startLocationToPoint(place.toMapObject())
        return true
    }

    private fun nameOf(place: MapObject, fallback: String): String =
        sequenceOf(place.title, place.address, place.subtitle).firstOrNull { !it.isNullOrBlank() } ?: fallback

    // What follows has no Android in it, for the unit tests.

    /** [place] first, any older entry for the same spot dropped, at most [max] kept. */
    fun added(list: List<Place>, place: Place, max: Int = MAX_RECENT): List<Place> =
        (listOf(place) + list.filterNot { near(it, place) }).take(max)

    fun near(a: Place, b: Place): Boolean {
        val north = abs(a.lat - b.lat) * 111_320.0
        val east = abs(a.lon - b.lon) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
        return north * north + east * east < SAME_PLACE_M * SAME_PLACE_M
    }

    /** One place a line: latitude, longitude, name, tab-separated. */
    fun line(place: Place): String =
        "${place.lat}\t${place.lon}\t${place.name.replace('\t', ' ').replace('\n', ' ')}"

    fun lines(places: List<Place>): String = places.joinToString("\n", transform = ::line)

    fun parse(line: String): Place? {
        val parts = line.split('\t', limit = 3)
        val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Place(parts.getOrNull(2).orEmpty(), lat, lon)
    }

    fun parseAll(text: String): List<Place> = text.split('\n').mapNotNull(::parse)
}
