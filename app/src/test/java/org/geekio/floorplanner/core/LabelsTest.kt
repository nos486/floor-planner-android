package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    @Test
    fun addMoveHitAndDelete() {
        val (p, label) = PlanOps.addLabel(Project(), "  North  ", P(100.0, 100.0), 20.0, arrowTip = P(300.0, 100.0))!!
        assertEquals("North", label.text)
        assertEquals(Selection(Kind.LABEL, label.id), PlanOps.hit(p, P(105.0, 102.0), 2.0))
        // Along the arrow too.
        assertEquals(label.id, PlanOps.hitLabel(p, P(250.0, 101.0), 2.0)?.id)
        val moved = PlanOps.moveLabel(p, label.id, P(10.0, -5.0)).label(label.id)!!
        assertEquals(P(110.0, 95.0), moved.anchor)
        assertEquals(P(310.0, 95.0), moved.tip)
        assertTrue(PlanOps.delete(p, Selection(Kind.LABEL, label.id)).labels.isEmpty())
    }

    @Test
    fun blankTextIsIgnored() {
        assertNull(PlanOps.addLabel(Project(), "   ", P.ZERO, 20.0))
    }

    @Test
    fun arrowHeadPointsBackFromTip() {
        val (a, b) = PlanOps.arrowHead(P(0.0, 0.0), P(100.0, 0.0), 10.0)
        assertEquals(90.0, a.x, 1e-9)
        assertEquals(90.0, b.x, 1e-9)
        assertEquals(-a.y, b.y, 1e-9)
    }

    @Test
    fun labelsSurviveJsonAndAppearInSvg() {
        val (p, _) = PlanOps.addLabel(Project(), "Pantry & <hall>", P(0.0, 0.0), 20.0, P(50.0, 50.0))!!
        val loaded = ProjectIO.fromJson(ProjectIO.toJson(p))!!
        assertEquals(p.labels, loaded.labels)
        val svg = ProjectIO.toSvg(loaded)
        assertTrue(svg.contains("Pantry &amp; &lt;hall&gt;"))
        assertTrue(svg.contains("<polygon points=\"50.0,50.0"))
    }
}
