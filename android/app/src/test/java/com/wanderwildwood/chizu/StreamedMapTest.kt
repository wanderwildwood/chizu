package com.wanderwildwood.chizu

import app.organicmaps.sdk.downloader.CountryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamedMapTest {
    @Test
    fun theWholeWorldAcrossOneTileIsZoomZero() {
        // 360 units across 512 pixels at density 1 is zoom 0; half the units per pixel is one zoom more.
        assertEquals(0.0, StreamedMap.webZoom(360.0 / 512.0, 1f), 1e-9)
        assertEquals(1.0, StreamedMap.webZoom(360.0 / 1024.0, 1f), 1e-9)
        // A denser screen shows the same ground at a lower zoom of the tile grid.
        assertEquals(13.0, StreamedMap.webZoom(360.0 / (512.0 * 2 * 8192), 2f), 1e-9)
    }

    @Test
    fun aMapThatIsOnThePhoneNeedsNoStream() {
        assertTrue(StreamedMap.isOnPhone(CountryItem.STATUS_DONE))
        assertTrue(StreamedMap.isOnPhone(CountryItem.STATUS_UPDATABLE))
        assertFalse(StreamedMap.isOnPhone(CountryItem.STATUS_DOWNLOADABLE))
        assertFalse(StreamedMap.isOnPhone(CountryItem.STATUS_PROGRESS))
        assertFalse(StreamedMap.isOnPhone(CountryItem.STATUS_FAILED))
    }

    @Test
    fun wifiOnlyHoldsTheFetchingOnMobileData() {
        assertTrue(StreamedMap.mayFetch(wifiOnly = false, onWifi = false))
        assertTrue(StreamedMap.mayFetch(wifiOnly = true, onWifi = true))
        assertFalse(StreamedMap.mayFetch(wifiOnly = true, onWifi = false))
    }

    @Test
    fun theStyleTakesTheServerAndTheLanguage() {
        val template = """{"url":"__TILES__","f":["get","name:__LANG__"]}"""
        assertEquals(
            """{"url":"https://tiles.openfreemap.org/planet","f":["get","name:de"]}""",
            StreamedMap.style(template, "", "de"),
        )
        assertEquals(
            """{"url":"https://maps.example.org/planet","f":["get","name:en"]}""",
            StreamedMap.style(template, "https://maps.example.org/planet", "en"),
        )
    }

    @Test
    fun geoJsonPutsLongitudeFirst() {
        assertEquals(
            """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[[-82.551500,35.595100],[-78.638200,35.779600]]}}""",
            StreamedMap.lineJson(listOf(doubleArrayOf(35.5951, -82.5515), doubleArrayOf(35.7796, -78.6382))),
        )
        assertEquals(
            """{"type":"Feature","properties":{"moving":true,"bearing":90.0},"geometry":{"type":"Point","coordinates":[-82.551500,35.595100]}}""",
            StreamedMap.pointJson(35.5951, -82.5515, 90f),
        )
        assertTrue(StreamedMap.pointJson(35.5951, -82.5515, null).contains("\"moving\":false"))
    }
}
