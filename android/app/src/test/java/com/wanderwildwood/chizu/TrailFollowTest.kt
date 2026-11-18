package com.wanderwildwood.chizu

import com.wanderwildwood.chizu.TrailFollow.Saved
import com.wanderwildwood.chizu.TrailFollow.Spot
import com.wanderwildwood.chizu.TrailFollow.Trail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailFollowTest {
    // A made-up place. One step north is 0.001 of latitude, about 111 m.
    private val lat0 = 39.7300
    private val lon0 = -121.8400
    private val step = 111.1949

    /** Points given as (meters north, meters east) of the made-up place. */
    private fun trail(vararg points: Pair<Double, Double>) = Trail(
        DoubleArray(points.size) { north(points[it].first) },
        DoubleArray(points.size) { east(points[it].second) },
    )

    private fun north(meters: Double) = lat0 + meters / 111_194.9
    private fun east(meters: Double) = lon0 + meters / (111_194.9 * Math.cos(Math.toRadians(lat0)))

    private val straight = trail(0.0 to 0.0, 500.0 to 0.0, 1000.0 to 0.0)

    @Test
    fun lengthIsTheSumOfItsPieces() {
        assertEquals(1000.0, straight.length, 1.0)
        assertEquals(1000.0 + 300.0, trail(0.0 to 0.0, 1000.0 to 0.0, 1000.0 to 300.0).length, 1.5)
    }

    @Test
    fun nearestPointIsOnThePieceNotAtItsEnds() {
        val spot = straight.locate(north(250.0), east(20.0), null)
        assertEquals(250.0, spot.along, 1.0)
        assertEquals(20.0, spot.away, 0.5)
    }

    @Test
    fun beforeTheStartAndPastTheEndStayOnTheTrail() {
        val before = straight.locate(north(-40.0), east(0.0), null)
        assertEquals(0.0, before.along, 0.01)
        assertEquals(40.0, before.away, 0.5)
        val past = straight.locate(north(1030.0), east(0.0), 990.0)
        assertEquals(straight.length, past.along, 0.01)
        assertEquals(30.0, past.away, 0.5)
    }

    @Test
    fun distanceLeftCountsFromTheNearestPoint() {
        val spot = straight.locate(north(700.0), east(-10.0), 690.0)
        assertEquals(300.0, TrailFollow.left(spot, straight.length), 1.0)
    }

    @Test
    fun turnedAroundTheLeftIsWhatWasBehind() {
        val back = straight.reversed()
        val spot = back.locate(north(700.0), east(0.0), null)
        assertEquals(300.0, spot.along, 1.0)
        assertEquals(700.0, TrailFollow.left(spot, back.length), 1.0)
    }

    @Test
    fun offTheTrailPastFiftyMetersUnlessThePositionIsThatUnsure() {
        assertFalse(TrailFollow.isOff(49.0, 10.0))
        assertTrue(TrailFollow.isOff(51.0, 10.0))
        assertFalse(TrailFollow.isOff(80.0, 100.0))
        assertTrue(TrailFollow.isOff(120.0, 100.0))
    }

    @Test
    fun theEndIsWithinThirtyMetersAndOnTheTrail() {
        assertTrue(TrailFollow.isAtEnd(Spot(980.0, 5.0), 1000.0))
        assertFalse(TrailFollow.isAtEnd(Spot(960.0, 5.0), 1000.0))
        assertFalse(TrailFollow.isAtEnd(Spot(1000.0, 80.0, off = true), 1000.0))
    }

    // Out to a hut and back the same way: every place is passed twice.
    private val outAndBack = trail(0.0 to 0.0, 1000.0 to 0.0, 0.0 to 0.0)

    @Test
    fun outAndBackStartsOnTheWayOut() {
        val spot = outAndBack.locate(north(300.0), east(3.0), null)
        assertEquals(300.0, spot.along, 1.0)
    }

    @Test
    fun outAndBackKeepsToTheLegBeingWalked() {
        // On the way out, 400 m in.
        assertEquals(400.0, outAndBack.locate(north(400.0), east(2.0), 390.0).along, 1.0)
        // On the way back at the same place: 1600 m walked, 400 m left.
        val back = outAndBack.locate(north(400.0), east(2.0), 1590.0)
        assertEquals(1600.0, back.along, 1.0)
        assertEquals(400.0, TrailFollow.left(back, outAndBack.length), 1.0)
    }

    @Test
    fun turningBackEarlyMovesToTheReturnLeg() {
        // Turned at 600 m instead of the hut, now 100 m back down: that is the return leg.
        val spot = outAndBack.locate(north(500.0), east(0.0), 600.0)
        assertEquals(1500.0, spot.along, 1.0)
    }

    @Test
    fun aLoopStartsAtItsStartNotItsEnd() {
        val loop = trail(0.0 to 0.0, 300.0 to 0.0, 300.0 to 300.0, 0.0 to 300.0, 0.0 to 0.0)
        val start = loop.locate(north(1.0), east(1.0), null)
        assertTrue(start.along < 5.0)
        // Coming round to the same place at the end of the walk.
        val end = loop.locate(north(0.0), east(4.0), loop.length - 20.0)
        assertEquals(loop.length - 4.0, end.along, 1.0)
    }

    @Test
    fun aShortCutAcrossABendJumpsAhead() {
        // A long hairpin: 1 km east, 100 m north, 1 km back west. The walker cuts across near the start.
        val hairpin = trail(0.0 to 0.0, 0.0 to 1000.0, 100.0 to 1000.0, 100.0 to 0.0)
        val spot = hairpin.locate(north(95.0), east(100.0), 100.0)
        assertEquals(2000.0, spot.along, 1.5)
    }

    @Test
    fun walkingBackTowardTheStartIsFollowedToo() {
        val spot = straight.locate(north(200.0), east(0.0), 800.0)
        assertEquals(200.0, spot.along, 1.0)
    }

    @Test
    fun aPointTwiceInARowDoesNoHarm() {
        val doubled = trail(0.0 to 0.0, 500.0 to 0.0, 500.0 to 0.0, 1000.0 to 0.0)
        assertEquals(1000.0, doubled.length, 1.0)
        assertEquals(600.0, doubled.locate(north(600.0), east(5.0), null).along, 1.0)
    }

    @Test
    fun followingStartsOnlyWithALine() {
        assertFalse(TrailFollow.start("Ridge loop", doubleArrayOf()))
        assertFalse(TrailFollow.start("Ridge loop", doubleArrayOf(lat0, lon0)))
        assertFalse(TrailFollow.isFollowing)
        assertTrue(TrailFollow.start("Ridge loop", doubleArrayOf(lat0, lon0, north(step), lon0)))
        assertTrue(TrailFollow.isFollowing)
        TrailFollow.stop()
        assertFalse(TrailFollow.isFollowing)
    }

    // What is kept while the app is closed.
    private val ridge = doubleArrayOf(39.7280, -121.8370, 39.7292, -121.8362, 39.7305, -121.8358)

    /** A longer made-up line: [count] points going north-east. */
    private fun long(count: Int) = DoubleArray(count * 2) { if (it % 2 == 0) lat0 + it * 0.0002 else lon0 + it * 0.0003 }

    @Test
    fun whatIsKeptReadsBackAsItWasWritten() {
        for (line in listOf(ridge, long(2), long(4), long(250))) {
            val forward = Saved.of(line, false)
            val read = Saved.parse(forward.line())!!
            assertEquals(forward.line(), read.line())
            assertFalse(read.reversed)
            assertTrue(read.isLineOf(line))
            val turned = Saved.parse(forward.turned(true).line())!!
            assertTrue(turned.reversed)
            assertTrue(turned.isLineOf(line))
        }
    }

    @Test
    fun nothingOrNonsenseKeptIsNoTrail() {
        assertNull(Saved.parse(null))
        assertNull(Saved.parse(""))
        assertNull(Saved.parse("1 3"))
        assertNull(Saved.parse("0 1 39.7 -121.8"))
        assertNull(Saved.parse("0 three 39.7 -121.8 39.8 -121.9"))
        val good = Saved.of(ridge, false).line()
        assertNull(Saved.parse("2" + good.substring(1)))
        assertNull(Saved.parse(good.substringBeforeLast(' ')))
        assertNull(Saved.parse(good.substringBeforeLast(' ') + " north"))
        assertNull(Saved.parse("$good 1.0"))
    }

    @Test
    fun theTrackIsKnownAgainByItsLine() {
        val saved = Saved.of(ridge, false)
        assertTrue(saved.isLineOf(ridge.copyOf()))
        // The same after a trip through a track file, which keeps a little less of each number.
        assertTrue(saved.isLineOf(DoubleArray(ridge.size) { ridge[it] + 0.000004 }))
        val many = long(250)
        assertTrue(Saved.of(many, true).isLineOf(DoubleArray(many.size) { many[it] - 0.000004 }))
    }

    @Test
    fun anotherTrackIsNotTakenForIt() {
        val saved = Saved.of(ridge, false)
        // Its middle point 11 m further north.
        assertFalse(saved.isLineOf(ridge.copyOf().also { it[2] += 0.0001 }))
        // Fewer points, or none: the track that was there is gone.
        assertFalse(saved.isLineOf(ridge.copyOf(4)))
        assertFalse(saved.isLineOf(doubleArrayOf()))
        // The same points the other way round are another line.
        val backward = doubleArrayOf(39.7305, -121.8358, 39.7292, -121.8362, 39.7280, -121.8370)
        assertFalse(saved.isLineOf(backward))
        // As many points somewhere else.
        assertFalse(Saved.of(long(250), false).isLineOf(DoubleArray(500) { long(250)[it] + 0.01 }))
    }

    @Test
    fun turningAroundIsKeptAsTheWayWalkedNotAsAnotherLine() {
        assertTrue(TrailFollow.start("Ridge loop", ridge))
        assertFalse(TrailFollow.isReversed)
        TrailFollow.turnAround()
        assertTrue(TrailFollow.isReversed)
        // The line kept is the track's own, whichever way it is walked.
        assertTrue(Saved.of(ridge, TrailFollow.isReversed).isLineOf(ridge))
        TrailFollow.turnAround()
        assertFalse(TrailFollow.isReversed)
        TrailFollow.stop()
        assertFalse(TrailFollow.isReversed)
    }

    // Part of a trail: from where the walker meets it to a spot chosen on it.

    @Test
    fun aPointAlongTheTrailLiesOnIt() {
        val (lat, lon) = straight.pointAt(250.0)
        assertEquals(north(250.0), lat, 0.000001)
        assertEquals(east(0.0), lon, 0.000001)
        val bend = trail(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0)
        val (bLat, bLon) = bend.pointAt(150.0)
        assertEquals(north(100.0), bLat, 0.000005)
        assertEquals(east(50.0), bLon, 0.000005)
        // Held to the ends.
        assertEquals(north(1000.0), straight.pointAt(5000.0).first, 0.000001)
        assertEquals(north(0.0), straight.pointAt(-10.0).first, 0.000001)
    }

    @Test
    fun aStretchRunsFromWhereItBeginsToTheStop() {
        val day = straight.part(200.0, 700.0)
        assertEquals(500.0, day.length, 1.0)
        // 300 m north is 100 m into the stretch, 400 m from its stop.
        val spot = day.locate(north(300.0), east(5.0), null)
        assertEquals(100.0, spot.along, 1.0)
        assertEquals(400.0, TrailFollow.left(spot, day.length), 1.0)
        // Past the stop is the stop.
        assertTrue(TrailFollow.isAtEnd(day.locate(north(710.0), east(0.0), 480.0), day.length))
    }

    @Test
    fun aStopBehindTheWalkerRunsTheStretchBackward() {
        val back = straight.part(700.0, 200.0)
        assertEquals(500.0, back.length, 1.0)
        assertEquals(north(700.0), back.pointAt(0.0).first, 0.000001)
        assertEquals(north(200.0), back.pointAt(back.length).first, 0.000001)
        val spot = back.locate(north(600.0), east(0.0), null)
        assertEquals(100.0, spot.along, 1.0)
        assertEquals(400.0, TrailFollow.left(spot, back.length), 1.0)
    }

    @Test
    fun aStretchKeepsTheBendsBetweenItsEnds() {
        val bend = trail(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 200.0 to 100.0)
        val part = bend.part(50.0, 250.0)
        assertEquals(200.0, part.length, 1.0)
        assertEquals(east(50.0), part.pointAt(100.0).second, 0.000005)
    }

    @Test
    fun aStretchAtOnePlaceIsStillALine() {
        val none = straight.part(400.0, 400.0)
        assertEquals(0.0, none.length, 0.001)
        assertTrue(TrailFollow.isAtEnd(none.locate(north(400.0), east(3.0), null), none.length))
    }

    @Test
    fun aStretchWaitsForAPositionBeforeItBegins() {
        assertTrue(TrailFollow.startPart("Ridge loop", ridge, 120.0))
        assertTrue(TrailFollow.isFollowing)
        assertTrue(TrailFollow.isStretch)
        assertEquals(120.0, TrailFollow.stopAt!!, 0.001)
        // Not begun: there is no way round yet.
        TrailFollow.turnAround()
        assertFalse(TrailFollow.isReversed)
        TrailFollow.stop()
        assertFalse(TrailFollow.isFollowing)
        assertFalse(TrailFollow.isStretch)
    }

    @Test
    fun aMarkAfterAJumpBetweenLinesStopsAtTheMarkedPoint() {
        // Two lines joined into one trail: 0..500 north, then a 100 m jump east, then 500 more north.
        val joined = trail(0.0 to 0.0, 500.0 to 0.0, 500.0 to 100.0, 1000.0 to 100.0)
        // The map measures 700 m to a mark 200 m up the second line, leaving the jump out.
        assertEquals(800.0, TrailFollow.stopOnTrail(joined, 700.0, north(700.0), east(100.0)), 1.5)
        // One line: the map's distance and the trail's agree.
        assertEquals(700.0, TrailFollow.stopOnTrail(straight, 700.0, north(700.0), east(0.0)), 1.0)
    }

    @Test
    fun aStopPastTheEndIsTheEnd() {
        assertTrue(TrailFollow.startPart("Ridge loop", ridge, 1.0e9))
        assertTrue(TrailFollow.stopAt!! < 1000.0)
        TrailFollow.stop()
        // The whole trail again after a stretch.
        assertTrue(TrailFollow.start("Ridge loop", ridge))
        assertFalse(TrailFollow.isStretch)
        TrailFollow.stop()
    }

    @Test
    fun aStretchIsKeptAndReadBack() {
        val waiting = Saved.parse(Saved.of(ridge, false).stretch(null, 120.5).line())!!
        assertNull(waiting.begin)
        assertEquals(120.5, waiting.stop!!, 0.0)
        assertTrue(waiting.isLineOf(ridge))
        val begun = Saved.parse(Saved.of(ridge, false).stretch(300.25, 120.5).line())!!
        assertEquals(300.25, begun.begin!!, 0.0)
        assertEquals(120.5, begun.stop!!, 0.0)
        assertTrue(begun.reversed)
        assertTrue(begun.isLineOf(ridge))
        // A whole trail kept before stretches existed reads as it did.
        val whole = Saved.parse(Saved.of(ridge, true).line())!!
        assertNull(whole.stop)
        assertTrue(whole.reversed)
    }

    @Test
    fun aBadlyKeptStretchIsNoTrail() {
        val good = Saved.of(ridge, false).line()
        assertNull(Saved.parse("$good part"))
        assertNull(Saved.parse("$good part - "))
        assertNull(Saved.parse("$good part - north"))
        assertNull(Saved.parse("$good part west 120"))
        assertNull(Saved.parse("$good park - 120"))
        assertNull(Saved.parse("$good part - 120 7"))
    }
}
