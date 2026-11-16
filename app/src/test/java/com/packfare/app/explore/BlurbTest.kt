package com.packfare.app.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurbTest {
    @Test fun matchesNeedsSharedWord() {
        assertTrue(BlurbText.matches("Jaisalmer Fort", "Jaisalmer Fort"))
        assertTrue(!BlurbText.matches("Government Museum Jaisalmer", "Tokyo National Museum".replace("Museum", "Park")))
    }
    @Test fun tidyCutsAtSentenceAndDropsBrackets() {
        val long = "Jaisalmer Fort (also called Sonar Quila) is a fort in Jaisalmer, Rajasthan. " + "It is built of yellow sandstone and stands on Trikuta Hill. ".repeat(6)
        val t = BlurbText.tidy(long)
        assertTrue(t.length <= 260 && t.endsWith("."))
        assertTrue(!t.contains("("))
        assertEquals("Short one.", BlurbText.tidy("Short one."))
    }
}
