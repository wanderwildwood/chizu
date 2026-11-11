package com.wanderwildwood.chizu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAddressTest {
    @Test
    fun anAddressHasANumberAndAWord() {
        assertTrue(OnlineAddress.looksLikeAddress("12 Mill Lane"))
        assertTrue(OnlineAddress.looksLikeAddress("Hauptstraße 5, Berlin"))
        assertTrue(OnlineAddress.looksLikeAddress("  221b Baker Street "))
        assertFalse(OnlineAddress.looksLikeAddress("cafe"))
        assertFalse(OnlineAddress.looksLikeAddress("95928"))
        assertFalse(OnlineAddress.looksLikeAddress("Mill Lane"))
        assertFalse(OnlineAddress.looksLikeAddress("5 7"))
        assertFalse(OnlineAddress.looksLikeAddress(null))
    }

    @Test
    fun theAddressGoesIntoTheLinkEncoded() {
        assertEquals(
            "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress?address=12+Mill+Lane%2C+Ada&benchmark=Public_AR_Current&format=json",
            OnlineAddress.censusUrl(" 12 Mill Lane, Ada "),
        )
        assertEquals("https://photon.komoot.io/api?q=Hauptstra%C3%9Fe+5&limit=3&lang=de", OnlineAddress.photonUrl("", "Hauptstraße 5", "de"))
        assertEquals("https://maps.example/api?q=1+Rynek&limit=3", OnlineAddress.photonUrl("https://maps.example/api", "1 Rynek", "pl"))
    }

    @Test
    fun readsTheCensusAnswer() {
        val json = """{"result":{"addressMatches":[{"coordinates":{"x":-76.9283,"y":38.8450},"matchedAddress":"12 MILL LN, ADA, DC, 20233"}]}}"""
        val found = OnlineAddress.parseCensus(json)
        assertEquals(1, found.size)
        assertEquals("12 Mill Ln, Ada, DC, 20233", found[0].name)
        assertEquals("cm://map?v=1&ll=38.845000,-76.928300&n=12%20Mill%20Ln%2C%20Ada%2C%20DC%2C%2020233&z=17", OnlineAddress.link(found[0]))
        assertEquals(38.8450, found[0].lat, 1e-9)
        assertEquals(-76.9283, found[0].lon, 1e-9)
        assertTrue(OnlineAddress.parseCensus("""{"result":{"addressMatches":[]}}""").isEmpty())
        assertEquals("100 Main St NW, Ada, DC, 20233", OnlineAddress.tidyCapitals("100 MAIN ST NW, ADA, DC, 20233"))
    }

    @Test
    fun readsThePhotonAnswer() {
        val json = """{"features":[{"geometry":{"coordinates":[21.0060,52.2386],"type":"Point"},"properties":{"street":"Millowa","housenumber":"10","postcode":"00-103","city":"Warsaw","country":"Poland"}},{"geometry":{"coordinates":[1.5,2.5]},"properties":{"name":"Mill House","city":"Ada"}}]}"""
        val found = OnlineAddress.parsePhoton(json)
        assertEquals(2, found.size)
        assertEquals("Millowa 10, 00-103, Warsaw, Poland", found[0].name)
        assertEquals(52.2386, found[0].lat, 1e-9)
        assertEquals(21.0060, found[0].lon, 1e-9)
        assertEquals("Mill House, Ada", found[1].name)
    }
}
