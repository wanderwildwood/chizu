package com.wanderwildwood.chizu.ui

import android.content.Context
import app.organicmaps.MwmApplication
import app.organicmaps.R
import app.organicmaps.sdk.Framework
import com.wanderwildwood.chizu.GoPlaces

/* What a recent destination's row says on the search screen: its name, and how far it is when the phone knows where it is. */
object RecentPlaceRow {
    @JvmStatic
    fun text(context: Context, place: GoPlaces.Place): String {
        val name = place.name.ifBlank { context.getString(R.string.go_place_unnamed) }
        val here = MwmApplication.from(context).locationHelper.savedLocation ?: return name
        val far = Framework.nativeGetDistanceAndAzimuthFromLatLon(place.lat, place.lon, here.latitude, here.longitude, 0.0)
            .distance.toString(context)
        return context.getString(R.string.go_recent_with_distance, name, far)
    }
}
