package com.wanderwildwood.chizu

import com.wanderwildwood.chizu.GoPlaces.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoPlacesTest {
    private val ada = Place("Ada Whitlock", 39.7296, -121.8440)
    private val forest = Place("Forest Avenue 12", 39.7262, -121.8015)

    @Test
    fun aPlaceComesBackAsItWasSaved() {
        assertEquals(listOf(ada, forest), GoPlaces.parseAll(GoPlaces.lines(listOf(ada, forest))))
        assertEquals(Place("", 1.5, -2.5), GoPlaces.parse(GoPlaces.line(Place("", 1.5, -2.5))))
    }

    @Test
    fun aNameCannotBreakTheLine() {
        val odd = Place("Two\tparts\nand a line", 10.0, 20.0)
        assertEquals(listOf(Place("Two parts and a line", 10.0, 20.0)), GoPlaces.parseAll(GoPlaces.lines(listOf(odd))))
    }

    @Test
    fun brokenLinesAreLeftOut() {
        assertNull(GoPlaces.parse("north\teast\tNowhere"))
        assertNull(GoPlaces.parse("95.0\t10.0\tToo far north"))
        assertNull(GoPlaces.parse(""))
        assertEquals(listOf(ada), GoPlaces.parseAll("junk\n" + GoPlaces.line(ada) + "\n"))
    }

    @Test
    fun theNewestDestinationIsFirstAndListedOnce() {
        val again = Place("Ada's house", 39.72962, -121.84402) // the same door, a few steps off
        assertEquals(listOf(again, forest), GoPlaces.added(listOf(ada, forest), again))
        assertEquals(listOf(forest, ada), GoPlaces.added(listOf(ada), forest))
    }

    @Test
    fun onlyTheLastFewAreKept() {
        val many = (1..20).map { Place("Stop $it", 40.0 + it, -120.0) }
        val kept = many.fold(emptyList<Place>()) { list, place -> GoPlaces.added(list, place) }
        assertEquals(GoPlaces.MAX_RECENT, kept.size)
        assertEquals("Stop 20", kept.first().name)
    }

    @Test
    fun nearMeansTheSameDoor() {
        assertTrue(GoPlaces.near(ada, Place("x", 39.7299, -121.8440)))   // ~33 m north
        assertFalse(GoPlaces.near(ada, Place("x", 39.7306, -121.8440)))  // ~110 m north
        assertFalse(GoPlaces.near(ada, forest))
    }
}
