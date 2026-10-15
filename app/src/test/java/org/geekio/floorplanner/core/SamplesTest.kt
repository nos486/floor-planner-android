package org.geekio.floorplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplesTest {
    private val p = Samples.home({ it.name }, { it.name })

    @Test
    fun twoFurnishedFloorsWithNamedRooms() {
        assertEquals(2, Floors.count(p))
        assertEquals(0, p.floorIndex)
        assertEquals(listOf("GROUND_FLOOR", "FIRST_FLOOR"), Floors.all(p).map { it.name })
        val expected = listOf(
            setOf("KITCHEN", "WC", "STUDY", "LIVING"),
            setOf("MASTER_BEDROOM", "BEDROOM", "BATHROOM", "FAMILY"),
        )
        Floors.all(p).forEachIndexed { i, f ->
            assertEquals(expected[i], f.rooms.map { it.name }.toSet())
            assertTrue(f.rooms.all { it.color != null && it.floor != null })
            assertTrue(f.items.size > 20)
            assertTrue(f.items.any { Furniture.isFixture(it) })
        }
        assertEquals(5, Floors.all(p)[1].rooms.size)
        assertEquals(1, Floors.all(p)[1].dimensions.size)
        assertEquals(1, p.labels.size)
    }

    @Test
    fun samplePassesThePlanCheck() {
        for (i in 0 until Floors.count(p)) {
            val issues = PlanCheck.run(Floors.switchTo(p, i))
            assertEquals("floor $i: $issues", emptyList<Issue>(), issues)
        }
    }

    @Test
    fun survivesSaving() {
        val back = ProjectIO.fromJson(ProjectIO.toJson(p))!!
        assertEquals(Floors.totalAreaM2(p), Floors.totalAreaM2(back), 0.01)
    }
}
