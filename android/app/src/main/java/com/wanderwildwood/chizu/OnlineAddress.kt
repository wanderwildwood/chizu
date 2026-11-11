package com.wanderwildwood.chizu

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * An address looked up on an address server, for a house number the maps on the phone do not have.
 *
 * Nothing is sent while typing. Search shows a row "Look it up online" under an address; a press on
 * it asks once whether the typed address may be sent, and after a yes sends it: to the geocoder of
 * the US Census Bureau when the phone or the map is in the United States, to Photon (Komoot, from
 * OpenStreetMap data) anywhere else and when the first finds nothing. Settings holds the switch and
 * the address of a Photon server of one's own. What came back is kept while the app runs, so the
 * same address is not asked for twice.
 */
object OnlineAddress {
    class Found(@JvmField val name: String, @JvmField val lat: Double, @JvmField val lon: Double)

    private const val PREFS = "atlas.online"
    private const val KEY_ON = "address.on" // Absent until the person has answered the question.
    private const val KEY_SERVER = "address.server"
    private const val CENSUS = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress"
    const val PHOTON = "https://photon.komoot.io/api"
    private const val USER_AGENT = "Atlas (com.wanderwildwood.chizu; https://github.com/wanderwildwood/chizu)"
    private const val UNITED_STATES = "United States of America"
    private const val MOST = 3

    private val kept = HashMap<String, List<Found>>()
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    @JvmStatic
    fun setOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ON, on).apply()

    private fun wasAsked(context: Context): Boolean = prefs(context).contains(KEY_ON)

    /** The address of a Photon server of one's own; empty means the default servers. */
    @JvmStatic
    fun server(context: Context): String = prefs(context).getString(KEY_SERVER, "").orEmpty()

    @JvmStatic
    fun setServer(context: Context, address: String) {
        val clean = OnlineRoutes.cleanServer(address)
        prefs(context).edit().apply { if (clean.isEmpty()) remove(KEY_SERVER) else putString(KEY_SERVER, clean) }.apply()
    }

    /** True when Search should offer the row: the words look like an address and the lookup is not switched off. */
    @JvmStatic
    fun offers(context: Context, typed: String?): Boolean =
        looksLikeAddress(typed) && (!wasAsked(context) || isOn(context))

    /** An address has a number in it and a word beside it: "12 Mill Lane", "Hauptstraße 5", not "cafe" or "95928". */
    @JvmStatic
    fun looksLikeAddress(typed: String?): Boolean {
        val words = typed?.trim().orEmpty().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (words.size < 2) return false
        return words.any { w -> w.any { it.isDigit() } } && words.any { w -> w.count { it.isLetter() } >= 2 }
    }

    @JvmStatic
    fun censusUrl(typed: String): String =
        "$CENSUS?address=${encode(typed)}&benchmark=Public_AR_Current&format=json"

    @JvmStatic
    fun photonUrl(server: String, typed: String, language: String): String {
        // Photon answers in these languages only; any other gets the names as they are written locally.
        val lang = if (language in setOf("en", "de", "fr")) "&lang=$language" else ""
        return "${server.ifEmpty { PHOTON }}?q=${encode(typed)}&limit=$MOST$lang"
    }

    private fun encode(text: String) = URLEncoder.encode(text.trim(), "UTF-8")

    /** The matches in the Census geocoder's answer. */
    @JvmStatic
    fun parseCensus(json: String): List<Found> {
        val matches = JSONObject(json).optJSONObject("result")?.optJSONArray("addressMatches") ?: return emptyList()
        val found = ArrayList<Found>()
        for (i in 0 until minOf(matches.length(), MOST)) {
            val match = matches.optJSONObject(i) ?: continue
            val at = match.optJSONObject("coordinates") ?: continue
            val name = tidyCapitals(match.optString("matchedAddress").trim())
            if (name.isEmpty() || !at.has("x") || !at.has("y")) continue
            found.add(Found(name, at.getDouble("y"), at.getDouble("x")))
        }
        return found
    }

    /**
     * "12 MILL LN NW, ADA, DC, 20233" as "12 Mill Ln NW, Ada, DC, 20233": the Census writes in capitals. The state
     * (the third part) and compass points keep theirs.
     */
    @JvmStatic
    fun tidyCapitals(address: String): String =
        address.split(", ").mapIndexed { part, text ->
            if (part == 2 && text.length == 2) text
            else text.split(" ").joinToString(" ") { word ->
                if (word in setOf("NE", "NW", "SE", "SW") || word.any { it.isLowerCase() }) word
                else word.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
        }.joinToString(", ")

    /** The places in a Photon answer (GeoJSON), each named by what it is and where. */
    @JvmStatic
    fun parsePhoton(json: String): List<Found> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        val found = ArrayList<Found>()
        for (i in 0 until minOf(features.length(), MOST)) {
            val feature = features.optJSONObject(i) ?: continue
            val at = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val p = feature.optJSONObject("properties") ?: continue
            val street = listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotEmpty() }.joinToString(" ")
            val parts = listOf(p.optString("name"), street, p.optString("postcode"), p.optString("city"), p.optString("state"), p.optString("country"))
                .filter { it.isNotEmpty() }.distinct()
            if (parts.isEmpty() || at.length() < 2) continue
            found.add(Found(parts.joinToString(", "), at.getDouble(1), at.getDouble(0)))
        }
        return found
    }

    /** A press on the row in Search: asks the first time, then looks [typed] up and shows what was found. */
    @JvmStatic
    fun lookUp(activity: Activity, typed: String) {
        if (isOn(activity)) return fetch(activity, typed.trim())
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(R.string.online_address_ask_title)
            .setMessage(R.string.online_address_ask_body)
            .setPositiveButton(R.string.online_address_use) { _, _ ->
                setOn(activity, true)
                fetch(activity, typed.trim())
            }
            .setNegativeButton(R.string.online_address_not_now, null)
            .show()
    }

    private fun inUnitedStates(activity: Activity): Boolean {
        fun there(lat: Double, lon: Double): Boolean {
            val id = MapManager.nativeFindCountry(lat, lon) ?: return false
            return id == UNITED_STATES || CountryItem.fill(id).topmostParentId == UNITED_STATES
        }
        val here = MwmApplication.from(activity).locationHelper.savedLocation
        if (here != null && there(here.latitude, here.longitude)) return true
        if (Framework.nativeGetDrawScale() == 0) return false
        val view = Framework.nativeGetViewport()
        return there(view[0], view[1])
    }

    private fun fetch(activity: Activity, typed: String) {
        kept[typed]?.let { return show(activity, typed, it) }
        val own = server(activity)
        val american = own.isEmpty() && inUnitedStates(activity)
        val language = Locale.getDefault().language
        Thread {
            var found: List<Found>? = null
            try {
                if (american) found = parseCensus(get(censusUrl(typed)))
                if (found.isNullOrEmpty()) found = parsePhoton(get(photonUrl(own, typed, language)))
            } catch (e: Exception) {
                // No answer, or one that cannot be read: said below.
                if (found.isNullOrEmpty()) found = null
            }
            val result = found
            Handler(Looper.getMainLooper()).post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                if (result == null) return@post say(activity, R.string.online_address_failed)
                kept[typed] = result
                show(activity, typed, result)
            }
        }.start()
    }

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { answer ->
            if (!answer.isSuccessful) throw java.io.IOException("HTTP ${answer.code}")
            answer.body?.string().orEmpty()
        }

    private fun show(activity: Activity, typed: String, found: List<Found>) {
        if (found.isEmpty()) return say(activity, R.string.online_address_none)
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setTitle(R.string.online_address_found)
            .setItems(found.map { it.name }.toTypedArray()) { _, which -> open(activity, found[which]) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun say(activity: Activity, words: Int) {
        MaterialAlertDialogBuilder(activity, R.style.MwmTheme_M3_AlertDialog)
            .setMessage(words)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    /** The link that shows [place] on the map under its own name, the way a map link from another app does. */
    @JvmStatic
    fun link(place: Found): String =
        String.format(Locale.ROOT, "cm://map?v=1&ll=%.6f,%.6f&n=%s&z=17", place.lat, place.lon, encode(place.name).replace("+", "%20"))

    private fun open(activity: Activity, place: Found) {
        val link = link(place)
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(activity.packageName))
        } catch (e: ActivityNotFoundException) {
            say(activity, R.string.online_address_failed)
        }
    }
}
