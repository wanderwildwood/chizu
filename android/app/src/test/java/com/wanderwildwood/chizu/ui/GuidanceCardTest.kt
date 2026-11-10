package com.wanderwildwood.chizu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class GuidanceCardTest {
    @Test
    fun speedMovesInFives() {
        assertEquals(0, GuidanceCard.inSteps(0))
        assertEquals(0, GuidanceCard.inSteps(2))
        assertEquals(5, GuidanceCard.inSteps(3))
        assertEquals(45, GuidanceCard.inSteps(47))
        assertEquals(50, GuidanceCard.inSteps(48))
        assertEquals(65, GuidanceCard.inSteps(65))
    }

    @Test
    fun theTwoTurnsAfterTheNextOne() {
        val steps = listOf(1, 2, 3, 4)
        assertEquals(listOf(1, 2), GuidanceCard.turnsAfter(steps, 1, 2))
        assertEquals(listOf(3), GuidanceCard.turnsAfter(steps, 3, 2))
        assertEquals(emptyList<Int>(), GuidanceCard.turnsAfter(steps, 4, 2))
    }

    @Test
    fun noRowsWithoutAKnownTurn() {
        assertEquals(emptyList<Int>(), GuidanceCard.turnsAfter(listOf(1, 2, 3), 0, 2))
        assertEquals(emptyList<Int>(), GuidanceCard.turnsAfter(listOf(1, 2, 3), 9, 2))
        assertEquals(emptyList<Int>(), GuidanceCard.turnsAfter(emptyList(), 1, 2))
    }

    @Test
    fun aStreetRowSaysItsNameOrNumber() {
        assertEquals("East 20th Street", GuidanceCard.rowText("East 20th Street", "", "", "", "", "Exit", "Turn right"))
        assertEquals("CA 32", GuidanceCard.rowText("", "CA 32", null, null, null, "Exit", "Turn right"))
        assertEquals("CA 99", GuidanceCard.rowText("", "CA 99;99", null, null, null, "Exit", "Exit"))
        assertEquals("Turn right", GuidanceCard.rowText(null, null, null, null, null, "Exit", "Turn right"))
        assertEquals("Exit", GuidanceCard.rowText(null, null, null, null, null, "Exit", " "))
    }

    @Test
    fun anExitRowSaysItsNumberAndWhereItLeads() {
        assertEquals("Exit 384, East 20th Street", GuidanceCard.rowText("East 20th Street", "", "384", "", "", "Exit", "Exit"))
        assertEquals("Exit 6A, US 101 San Jose",
            GuidanceCard.rowText("", "", "6A", "US 101", "San Jose; San Francisco International Airport", "Exit", "Exit"))
        assertEquals("Exit 12", GuidanceCard.rowText(null, null, "12", null, null, "Exit", "Exit"))
        assertEquals("Exit, Chico", GuidanceCard.rowText(null, null, null, null, "Chico", "Exit", "Exit"))
        assertEquals("Exit 3, CA 99", GuidanceCard.rowText("CA 99", "CA 99", "3", "", "", "Exit", "Exit"))
    }
}
