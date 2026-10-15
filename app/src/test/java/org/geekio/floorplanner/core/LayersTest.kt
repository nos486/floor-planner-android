package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Item
import org.geekio.floorplanner.model.Label
import org.geekio.floorplanner.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LayersTest {
    private val p = Project(
        items = listOf(Item("a", "sofa3", 0.0, 0.0, 210.0, 90.0), Item("b", "socket", 0.0, 0.0, 20.0, 12.0), Item("c", "drain", 0.0, 0.0, 20.0, 20.0)),
        dimensions = listOf(Dimensions.create(P(0.0, 0.0), P(100.0, 0.0))),
        labels = listOf(Label("l", "Hi", 0.0, 0.0)),
    )

    @Test
    fun hidesByLayer() {
        assertSame(p, Layers.visible(p, emptySet()))
        assertEquals(listOf("a", "c"), Layers.visible(p, setOf(Layer.ELECTRICAL)).items.map { it.id })
        assertEquals(listOf("b"), Layers.visible(p, setOf(Layer.FURNITURE, Layer.PLUMBING)).items.map { it.id })
        val bare = Layers.visible(p, setOf(Layer.DIMENSIONS, Layer.LABELS))
        assertEquals(0, bare.dimensions.size + bare.labels.size)
        assertEquals(3, bare.items.size)
    }
}
