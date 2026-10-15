package org.geekio.floorplanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    @Test
    fun everyTemplateBuildsWithNamedRoomsAndFittingOpenings() {
        for (t in Templates.all) {
            val p = Templates.build(t.key, t.key) { it.name }
            assertNotNull(t.key, p)
            p!!
            assertEquals(t.key, t.rooms, p.rooms.size)
            assertTrue(t.key, p.rooms.none { it.name.startsWith("Room ") })
            for (o in p.doors + p.windows) {
                val wall = p.wall(o.wallId)!!
                assertTrue("${t.key} ${o.id}", o.offset - o.width / 2 >= 0 && o.offset + o.width / 2 <= wall.length)
            }
            // Rooms are measured to the wall centre lines, so they add up to the outline.
            assertEquals(t.key, t.area.toDouble(), p.totalAreaM2, 0.05)
        }
    }

    @Test
    fun unknownTemplate() {
        assertNull(Templates.build("castle", "x") { "" })
    }
}
