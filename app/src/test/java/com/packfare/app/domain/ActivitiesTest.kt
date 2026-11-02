package com.packfare.app.domain

import com.packfare.app.explore.WikivoyageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitiesTest {

    private fun d(name: String) = Destinations.find(name)!!

    @Test
    fun `Jim Corbett gets checked safari ideas with prices`() {
        val ideas = Activities.ideasFor(d("Jim Corbett"), emptyList())
        assertTrue(ideas.size >= 6)
        val jeep = ideas.first { it.name.startsWith("Jeep safari") }
        assertEquals(IdeaSource.CURATED, jeep.source)
        assertEquals(60_000L, jeep.lowPaise)
        assertEquals(1_20_000L, jeep.highPaise)
        assertEquals("≈₹600–1,200", jeep.costLabel)
        assertTrue(ideas.any { it.name.contains("Garjiya") && it.costLabel == "free" })
    }

    @Test
    fun `guide listings with prices come before unpriced ones and typical ideas only top up`() {
        val guide = listOf(
            ActivityIdea("Old Manali walk", null, null, "", IdeaSource.GUIDE),
            ActivityIdea("Solang Valley", 50_000L, 2_00_000L, "", IdeaSource.GUIDE),
        )
        val ideas = Activities.ideasFor(d("Manali"), guide)
        assertEquals("Solang Valley", ideas[0].name)
        assertEquals("Old Manali walk", ideas[1].name)
        assertTrue(ideas.drop(2).all { it.source == IdeaSource.TYPICAL })
        assertEquals(ideas.map { it.name.lowercase() }.distinct().size, ideas.size)
    }

    @Test
    fun `every hub gets something to do`() {
        assertTrue(Destinations.hubs.all { Activities.ideasFor(it, emptyList()).isNotEmpty() })
    }

    @Test
    fun `cost labels read naturally`() {
        assertEquals("free", ActivityIdea("a", 0L).costLabel)
        assertEquals("≈₹500", ActivityIdea("a", 50_000L).costLabel)
        assertEquals("free to ≈₹100", ActivityIdea("a", 0L, 10_000L).costLabel)
        assertEquals("price varies", ActivityIdea("a").costLabel)
    }

    @Test
    fun `guide listings keep Indian and foreign prices as a range`() {
        val text = """
            ==See==
            * {{see | name=Fort | price=₹50 Indians, ₹500 foreigners }}
            * {{do | name=Sunset point | price=Free }}
            * {{do | name=River walk }}
            ==Sleep==
            * {{sleep | name=Zostel | price=₹600 }}
        """.trimIndent()
        val a = WikivoyageParser.activities(text)
        assertEquals(listOf("Fort", "Sunset point", "River walk"), a.map { it.name })
        assertEquals(5_000L, a[0].lowPaise)
        assertEquals(50_000L, a[0].highPaise)
        assertEquals(0L, a[1].lowPaise)
        assertNull(a[2].lowPaise)
        assertEquals(IdeaSource.GUIDE, a[0].source)
    }
}
