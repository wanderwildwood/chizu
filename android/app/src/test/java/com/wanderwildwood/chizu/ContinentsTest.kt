package com.wanderwildwood.chizu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentsTest {
    @Test
    fun aCountryIsInOneContinentOnly() {
        val all = Continents.ALL.flatMap { it.countries }
        assertEquals(all.size, all.toSet().size)
        assertTrue(all.size > 200)
    }

    @Test
    fun findsTheContinentByTheFileName() {
        assertEquals(Continents.of("United States of America"), Continents.of("Canada"))
        assertTrue(Continents.of("France") !== Continents.of("Japan"))
        assertNull(Continents.of("Atlantis"))
    }
}
