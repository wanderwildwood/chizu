package com.wanderwildwood.chizu

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.Framework
import app.organicmaps.sdk.downloader.CountryItem
import app.organicmaps.sdk.downloader.MapManager
import app.organicmaps.sdk.routing.RoutingController
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/**
 * The streamed map: where no map is on the phone, map tiles from a tile server are drawn over the
 * map, following it. Atlas's own map stays underneath and keeps every touch, so panning, zooming,
 * routes and guidance work as they do on a downloaded map.
 *
 * Only what is on the screen is fetched, nothing ahead. Nothing is fetched until the person has
 * switched the streamed map on: the drawing library is not even started before that.
 */
object StreamedMap {
    private const val PREFS = "atlas.online"
    private const val KEY_ON = "map.on" // Absent until the person has answered the question.
    private const val KEY_SERVER = "map.server"
    private const val KEY_WIFI_ONLY = "map.wifi"
    const val DEFAULT_TILES = "https://tiles.openfreemap.org/planet"
    private const val USER_AGENT = "Atlas (com.wanderwildwood.chizu; https://github.com/wanderwildwood/chizu)"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @JvmStatic
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, false)

    @JvmStatic
    fun setOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ON, on).apply()

    @JvmStatic
    fun wasAsked(context: Context): Boolean = prefs(context).contains(KEY_ON)

    /** True when the streamed map may fetch over Wi-Fi only; on mobile data it shows what it has kept. */
    @JvmStatic
    fun isWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, false)

    @JvmStatic
    fun setWifiOnly(context: Context, wifiOnly: Boolean) = prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()

    /** Whether the streamed map may fetch now. */
    @JvmStatic
    fun mayFetch(wifiOnly: Boolean, onWifi: Boolean): Boolean = !wifiOnly || onWifi

    /** The address of a tile server of one's own (its TileJSON); empty means the default server. */
    @JvmStatic
    fun server(context: Context): String = prefs(context).getString(KEY_SERVER, "").orEmpty()

    @JvmStatic
    fun setServer(context: Context, address: String) {
        val clean = OnlineRoutes.cleanServer(address)
        prefs(context).edit().apply { if (clean.isEmpty()) remove(KEY_SERVER) else putString(KEY_SERVER, clean) }.apply()
    }

    /** The zoom of a web map that shows [unitsPerPixel] Mercator units (360 across the world) per pixel. */
    @JvmStatic
    fun webZoom(unitsPerPixel: Double, density: Float): Double =
        ln(360.0 / (unitsPerPixel * 512.0 * density)) / ln(2.0)

    /** True when the map file with this download [status] is on the phone. */
    @JvmStatic
    fun isOnPhone(status: Int): Boolean =
        status == CountryItem.STATUS_DONE || status == CountryItem.STATUS_UPDATABLE || status == CountryItem.STATUS_APPLYING

    /** The style, with the tile address and the language of the names filled in. */
    @JvmStatic
    fun style(template: String, tiles: String, language: String): String =
        template.replace("__TILES__", tiles.ifEmpty { DEFAULT_TILES }).replace("__LANG__", language)

    /** A GeoJSON line through [points], given as latitude, longitude pairs. */
    @JvmStatic
    fun lineJson(points: List<DoubleArray>): String {
        val sb = StringBuilder("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        points.forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append('[').append(String.format(Locale.ROOT, "%.6f,%.6f", p[1], p[0])).append(']')
        }
        return sb.append("]}}").toString()
    }

    @JvmStatic
    fun pointJson(lat: Double, lon: Double, bearing: Float?): String = String.format(
        Locale.ROOT,
        "{\"type\":\"Feature\",\"properties\":{\"moving\":%s,\"bearing\":%.1f},\"geometry\":{\"type\":\"Point\",\"coordinates\":[%.6f,%.6f]}}",
        if (bearing != null) "true" else "false", bearing ?: 0f, lon, lat,
    )

    private const val EMPTY = "{\"type\":\"FeatureCollection\",\"features\":[]}"

    /** Below this zoom the map of the world that every phone carries is enough. */
    private const val MIN_ZOOM = 8.5
    private const val TICK_MS = 250L
    private const val ROUTE_REFRESH_MS = 20_000L
    private const val NETWORK_CHECK_MS = 2_000L

    /** The streamed map of one map screen. Made by the activity, told of its lifecycle. */
    class Layer(private val activity: Activity, private val frame: ViewGroup) {
        private val handler = Handler(Looper.getMainLooper())
        private var mapView: MapView? = null
        private var map: MapLibreMap? = null
        private var style: Style? = null
        private var started = false
        private var resumed = false
        private var shown = false
        private var last = DoubleArray(4)
        private var lastPosition = ""
        private var selected = EMPTY
        private var lastSelected = ""
        private var routeAt = 0L
        private var routeDirty = true
        private var hadRoute = false
        private var networkAt = 0L
        private var fetching: Boolean? = null
        private val density = activity.resources.displayMetrics.density

        private val tick = object : Runnable {
            override fun run() {
                update()
                if (resumed) handler.postDelayed(this, TICK_MS)
            }
        }

        fun onStart() { started = true; mapView?.onStart() }
        fun onResume() {
            resumed = true
            mapView?.onResume()
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
        fun onPause() { resumed = false; handler.removeCallbacks(tick); mapView?.onPause() }
        fun onStop() { started = false; mapView?.onStop() }
        fun onDestroy() { handler.removeCallbacks(tick); mapView?.onDestroy(); mapView = null; map = null; style = null }
        fun onLowMemory() { mapView?.onLowMemory() }

        /** The place whose page is open: marked on the streamed map, as Atlas marks it on its own. */
        fun setSelected(lat: Double, lon: Double) { selected = pointJson(lat, lon, null) }

        fun clearSelected() { selected = EMPTY }

        /** A route was built or rebuilt: its line is read again at the next tick. */
        fun onRouteChanged() { routeDirty = true }

        private fun update() {
            if (!isOn(activity) || Framework.nativeGetDrawScale() == 0) return hide()
            val v = Framework.nativeGetViewport()
            val zoom = webZoom(v[2], density)
            if (zoom.isNaN() || zoom.isInfinite() || zoom < MIN_ZOOM) return hide()
            // Out at sea no map file covers the middle of the screen and the name comes back empty:
            // the map engine stops the app when asked for the status of a map it has no name for.
            val country = MapManager.nativeFindCountry(v[0], v[1])
            if (!country.isNullOrEmpty() && isOnPhone(MapManager.nativeGetStatus(country))) return hide()

            checkNetwork()
            // Shown before the map is ready: the drawing surface is only made for a view that is visible.
            if (!shown) {
                shown = true
                frame.visibility = View.VISIBLE
                last = DoubleArray(4)
            }
            val m = ensureMap() ?: return
            if (abs(v[0] - last[0]) > 1e-7 || abs(v[1] - last[1]) > 1e-7 || abs(v[2] - last[2]) > last[2] * 1e-4 ||
                abs(v[3] - last[3]) > 0.05) {
                last = v
                m.moveCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(v[0], v[1])).zoom(zoom).bearing(v[3]).tilt(0.0).build(),
                    ),
                )
            }
            updatePosition()
            updateRoute()
            updateSelected()
        }

        private fun updateSelected() {
            val s = style ?: return
            if (selected == lastSelected) return
            lastSelected = selected
            s.getSourceAs<GeoJsonSource>(SRC_SELECTED)?.setGeoJson(selected)
        }

        /**
         * With "Wi-Fi only" on and no Wi-Fi, the drawing library is told there is no connection: it
         * draws the tiles it has kept and fetches nothing.
         */
        private fun checkNetwork() {
            val now = System.currentTimeMillis()
            if (fetching != null && now - networkAt < NETWORK_CHECK_MS) return
            networkAt = now
            val may = mayFetch(isWifiOnly(activity), onWifi())
            if (may == fetching) return
            fetching = may
            MapLibre.getInstance(activity.applicationContext)
            MapLibre.setConnected(if (may) null else false) // null: the library watches the connection itself.
        }

        private fun onWifi(): Boolean {
            val manager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }

        private fun hide() {
            if (!shown) return
            shown = false
            frame.visibility = View.GONE
        }

        private fun updatePosition() {
            val s = style ?: return
            val location = MwmApplication.from(activity).locationHelper.savedLocation
            val json = if (location == null) EMPTY
            else pointJson(location.latitude, location.longitude, if (location.hasBearing()) location.bearing else null)
            if (json == lastPosition) return
            lastPosition = json
            s.getSourceAs<GeoJsonSource>(SRC_POSITION)?.setGeoJson(json)
        }

        private fun updateRoute() {
            val s = style ?: return
            val routing = RoutingController.get()
            val hasRoute = routing.isBuilt || routing.isNavigating
            val now = System.currentTimeMillis()
            if (!hasRoute) {
                if (hadRoute) s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(EMPTY)
                hadRoute = false
                routeDirty = true
                return
            }
            // A new route arrives through onRouteChanged; the timer catches a reroute that did not say so.
            if (hadRoute && !routeDirty && now - routeAt < ROUTE_REFRESH_MS) return
            val points = Framework.nativeGetRouteJunctionPoints(1e9) ?: return
            hadRoute = true
            routeDirty = false
            routeAt = now
            s.getSourceAs<GeoJsonSource>(SRC_ROUTE)?.setGeoJson(lineJson(points.map { doubleArrayOf(it.mLat, it.mLon) }))
        }

        /** Starts the drawing library the first time the streamed map is needed. */
        private fun ensureMap(): MapLibreMap? {
            map?.let { return it }
            if (mapView != null) return null // Asked for, not ready yet.
            MapLibre.getInstance(activity.applicationContext)
            HttpRequestUtil.setOkHttpClient(
                OkHttpClient.Builder().addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
                }.build(),
            )
            val options = MapLibreMapOptions.createFromAttributes(activity)
                .textureMode(true)
                .pixelRatio(density)
                .compassEnabled(false).logoEnabled(false).attributionEnabled(false)
                .rotateGesturesEnabled(false).scrollGesturesEnabled(false).tiltGesturesEnabled(false)
                .zoomGesturesEnabled(false).doubleTapGesturesEnabled(false).quickZoomGesturesEnabled(false)
            val view = MapView(activity, options)
            mapView = view
            frame.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.onCreate(null)
            if (started) view.onStart()
            if (resumed) view.onResume()
            view.getMapAsync { ready ->
                ready.setPrefetchesTiles(false) // Nothing is fetched ahead of what is looked at.
                val template = activity.resources.openRawResource(R.raw.stream_style).bufferedReader().use { it.readText() }
                ready.setStyle(Style.Builder().fromJson(style(template, server(activity), Locale.getDefault().language))) { loaded ->
                    loaded.transition = TransitionOptions(0, 0, false) // E Ink: no fades.
                    addOwnLayers(loaded)
                    style = loaded
                    map = ready
                    last = DoubleArray(4)
                    lastPosition = ""
                    lastSelected = ""
                    routeDirty = true
                }
            }
            return null
        }

        private fun addOwnLayers(s: Style) {
            s.addSource(GeoJsonSource(SRC_ROUTE))
            s.addSource(GeoJsonSource(SRC_POSITION))
            s.addSource(GeoJsonSource(SRC_SELECTED))
            val below = "label-water" // The first of the names: the route lies under every name.
            s.addLayerBelow(
                LineLayer("route-edge", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor("#ffffff"), PropertyFactory.lineWidth(11f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ),
                below,
            )
            s.addLayerBelow(
                LineLayer("route", SRC_ROUTE).withProperties(
                    PropertyFactory.lineColor("#000000"), PropertyFactory.lineWidth(7f),
                    PropertyFactory.lineCap("round"), PropertyFactory.lineJoin("round"),
                ),
                below,
            )
            s.addImage(IMG_RING, ring())
            s.addLayer(
                SymbolLayer("selected", SRC_SELECTED).withProperties(
                    PropertyFactory.iconImage(IMG_RING),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                ),
            )
            s.addImage(IMG_ARROW, arrow(moving = true))
            s.addImage(IMG_DOT, arrow(moving = false))
            s.addLayer(
                SymbolLayer("position", SRC_POSITION).withProperties(
                    PropertyFactory.iconImage(
                        org.maplibre.android.style.expressions.Expression.switchCase(
                            org.maplibre.android.style.expressions.Expression.get("moving"),
                            org.maplibre.android.style.expressions.Expression.literal(IMG_ARROW),
                            org.maplibre.android.style.expressions.Expression.literal(IMG_DOT),
                        ),
                    ),
                    PropertyFactory.iconRotate(org.maplibre.android.style.expressions.Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment("map"),
                    PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconIgnorePlacement(true),
                ),
            )
        }

        /** The chosen place: a wide ring in ink with a white edge, open in the middle so the map shows through. */
        private fun ring(): Bitmap {
            val size = (44 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val c = size / 2f
            val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 9 * density }
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5 * density }
            canvas.drawCircle(c, c, size * 0.32f, white)
            canvas.drawCircle(c, c, size * 0.32f, ink)
            canvas.drawCircle(c, c, 2.5f * density, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
            return bitmap
        }

        /** Where the phone is: an arrow in ink while it moves, a ringed dot while it stands. */
        private fun arrow(moving: Boolean): Bitmap {
            val size = (40 * density).toInt()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3 * density; strokeJoin = Paint.Join.ROUND
            }
            val c = size / 2f
            if (moving) {
                val path = Path().apply {
                    moveTo(c, size * 0.08f); lineTo(size * 0.86f, size * 0.90f)
                    lineTo(c, size * 0.70f); lineTo(size * 0.14f, size * 0.90f); close()
                }
                canvas.drawPath(path, edge)
                canvas.drawPath(path, fill)
            } else {
                canvas.drawCircle(c, c, size * 0.30f, edge)
                canvas.drawCircle(c, c, size * 0.30f, fill)
                canvas.drawCircle(c, c, size * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            }
            return bitmap
        }

        private companion object {
            const val SRC_ROUTE = "atlas-route"
            const val SRC_POSITION = "atlas-position"
            const val SRC_SELECTED = "atlas-selected"
            const val IMG_RING = "atlas-ring"
            const val IMG_ARROW = "atlas-arrow"
            const val IMG_DOT = "atlas-dot"
        }
    }
}
