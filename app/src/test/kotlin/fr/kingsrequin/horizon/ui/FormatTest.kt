package fr.kingsrequin.horizon.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun durationsReadInPlainWords() {
        assertEquals(Ago.Now, agoOf(20_000))
        assertEquals(Ago.Now, agoOf(-5_000))
        assertEquals(Ago.Minutes(1), agoOf(60_000))
        assertEquals(Ago.Minutes(2), agoOf(125_000))
        assertEquals(Ago.Minutes(59), agoOf(3_599_000))
        assertEquals(Ago.Hours(3), agoOf(3 * 3_600_000 + 5))
        assertEquals(Ago.Hours(23), agoOf(24 * 3_600_000 - 1))
        assertEquals(Ago.Days(2), agoOf(2 * 86_400_000 + 7))
    }
}
