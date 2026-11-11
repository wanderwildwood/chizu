package com.wanderwildwood.chizu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixHelpTest {
    @Test
    fun saysSoOnlyAfterAWait() {
        assertFalse(FixHelp.isSlow(0))
        assertFalse(FixHelp.isSlow(19_999))
        assertTrue(FixHelp.isSlow(20_000))
    }
}
