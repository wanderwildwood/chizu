package com.wanderwildwood.chizu

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactPlacesTest {
    @Test fun anAddressOverSeveralLinesBecomesOne() {
        assertEquals("12 Elm Street, Ukiah, CA 95482", ContactPlaces.oneLine("12 Elm Street\nUkiah, CA 95482\n"))
        assertEquals("12 Elm Street, Ukiah", ContactPlaces.oneLine("12 Elm Street,\r\n  Ukiah  "))
        assertEquals("", ContactPlaces.oneLine(null))
    }

    @Test fun wildcardsInWhatWasTypedAreLiteral() {
        assertEquals("50\\% off\\_now", ContactPlaces.escape("50% off_now"))
        assertEquals("a\\\\b", ContactPlaces.escape("a\\b"))
    }
}
