package com.packfare.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V019Test {
    private fun d(n: String) = Destinations.find(n)!!

    @Test fun journeysCoverModes() {
        assertEquals(Journey.TRAIN, Journey.of(TransportMode.TRAIN_2AC))
        assertEquals(Journey.BUS, Journey.of(TransportMode.BUS_AC_SLEEPER))
        assertEquals(Journey.TAXI, Journey.of(TransportMode.SHARED_JEEP))
        assertEquals(Journey.TAXI, Journey.of(TransportMode.TAXI))
    }

    @Test fun delhiToManaliHasEveryKindSortedByPrice() {
        val o = LegOptions.forLeg(d("Delhi"), d("Manali"), 2)
        assertTrue(Journey.BUS in o && Journey.TAXI in o)
        o.values.forEach { l -> assertEquals(l.sortedBy { it.perPersonPaise }, l) }
        val taxi = o.getValue(Journey.TAXI).first { it.mode == TransportMode.TAXI }
        assertEquals(taxi.groupPaise, 2 * taxi.perPersonPaise)
    }

    @Test fun bigGroupsNeedMoreCabs() {
        val one = LegOptions.taxi(d("Delhi"), d("Agra"), 4)!!
        val two = LegOptions.taxi(d("Delhi"), d("Agra"), 5)!!
        assertEquals(2 * one.groupPaise, two.groupPaise)
        assertTrue(one.groupPaise >= 1_500_00L)
    }

    @Test fun defaultCap() {
        val o = LegOptions.forLeg(d("Delhi"), d("Manali"), 1)
        assertEquals(123_00L, LegOptions.defaultCapPaise(123_00L, o))
        assertEquals(o.values.flatten().minOf { it.perPersonPaise }, LegOptions.defaultCapPaise(0, o))
    }
}
