package com.packfare.app.explore

import com.packfare.app.domain.DailyCosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikivoyageParserTest {

    private val sample = """
        {{pagebanner|Manali banner.jpg}}
        {{geo|32.2432|77.1892|zoom=13}}
        '''Manali''' is a hill station in Himachal Pradesh.

        ==Eat==
        * {{eat | name=Madras Mess | address=Mall Rd | price=₹80 | content=Cheap South Indian thalis. }}
        * {{eat | name=Chopsticks | price=Rs 250-400 | content=Tibetan, see [[Old Manali|the old town]]. }}
        * {{eat | name=Dylan's | price=₹150 | content=Cookies {{convert|2|km}} from bus stand. }}
        * {{eat | name=Fancy Resort | price=₹2300 }}
        * {{eat | name=No price place | content=Good momos. }}

        ==Sleep==
        * {{sleep | name=Apple View Guest House | price=₹300-400 | content=Basic rooms. }}
        * {{sleep | name=Footloose Camps | price=₹500-900 per bed | content=Dorm and tents. }}
        * {{sleep | name=Zostel Manali | price=Dorm ₹650, private ₹2,200 }}
        * {{listing | type=sleep | name=Hostel X | price=INR 550 | content=8-bed dormitory. }}
        * {{sleep | name=Kapoor Resort | price=₹1200 per night for 2 persons }}
        * {{sleep | name=Open 24 hours | price=US${'$'}30 }}
    """.trimIndent()

    @Test
    fun `reads sleep and eat prices including listing type and ranges`() {
        val stats = WikivoyageParser.parse(sample)
        assertEquals(listOf(30_000L, 50_000L, 65_000L, 55_000L, 1_20_000L), stats.sleep.map { it.pricePaise })
        assertEquals(listOf("Footloose Camps", "Zostel Manali", "Hostel X"), stats.sleep.filter { it.isDorm }.map { it.name })
        assertEquals(listOf(8_000L, 25_000L, 15_000L, 2_30_000L), stats.eat.map { it.pricePaise })
        assertEquals(55_000L, stats.dormMedianPaise)
        assertEquals(20_000L, stats.mealMedianPaise) // median of 80,150,250,2300 -> (150+250)/2
    }

    @Test
    fun `refined costs use dorm median and three meals`() {
        val base = DailyCosts(55_000, 55_000, 20_000, 25_000)
        val refined = WikivoyageParser.refine(base, WikivoyageParser.parse(sample))
        assertEquals(55_000L, refined.stayPaise)
        assertEquals(60_000L, refined.foodPaise)
        assertEquals(20_000L, refined.localTransportPaise)
    }

    @Test
    fun `too little data falls back to the table`() {
        val base = DailyCosts(40_000, 45_000, 15_000, 15_000)
        val refined = WikivoyageParser.refine(base, WikivoyageParser.parse("* {{sleep | name=A | price=₹999 }}"))
        assertEquals(base, refined)
    }

    @Test
    fun `coordinates and rupee extraction`() {
        assertEquals(32.2432 to 77.1892, WikivoyageParser.coordinates(sample))
        assertNull(WikivoyageParser.coordinates("no geo here"))
        assertEquals(30_000L, WikivoyageParser.cheapestRupees("₹300-400"))
        assertEquals(1_80_000L, WikivoyageParser.cheapestRupees("1,800 rupees"))
        assertNull(WikivoyageParser.cheapestRupees("open 24 hours"))
        assertNull(WikivoyageParser.cheapestRupees("US${'$'}30"))
        assertTrue(WikivoyageParser.parse("").sampleCount == 0)
    }

    @Test
    fun `highlights come from see and do listings`() {
        val text = """
            ==See==
            * {{see | name=[[Hadimba Temple|Hadimba Devi Temple]] | content=Wooden temple. }}
            * {{see | name='''Old Manali''' | content=Cafes. }}
            ==Do==
            * {{do | name=Solang Valley | price=₹500 }}
            * {{listing | type=do | name=Paragliding }}
            ==Sleep==
            * {{sleep | name=Zostel | price=₹600 dorm }}
        """.trimIndent()
        assertEquals(
            listOf("Hadimba Devi Temple", "Old Manali", "Solang Valley", "Paragliding"),
            WikivoyageParser.highlights(text),
        )
        // See/do prices never leak into sleep/eat stats.
        assertEquals(1, WikivoyageParser.parse(text).sampleCount)
    }
}

class GuideNoteTest {
    @org.junit.Test fun realPlacesCarryNotes() {
        val wt = """
==See==
* {{see | name=[[Jaisalmer Fort]] | alt=Sonar Quila | url= | price=₹50 | hours=Sunrise-sunset
| content=A living fort of golden sandstone, with Jain temples, havelis and shops inside its walls.}}
* {{see | name=Government Museum | price=Free | content=Small museum of local history.}}
"""
        val a = WikivoyageParser.activities(wt)
        org.junit.Assert.assertEquals("Jaisalmer Fort", a[0].name)
        org.junit.Assert.assertTrue(a[0].note.contains("golden sandstone") && a[0].note.contains("Hours: Sunrise-sunset"))
        org.junit.Assert.assertEquals("Small museum of local history.", a[1].note)
    }
}
