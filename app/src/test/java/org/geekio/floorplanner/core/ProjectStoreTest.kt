package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val plan = Project(
        projectName = "Flat",
        walls = listOf(Wall.create("w1", P(0.0, 0.0), P(300.0, 0.0), 20.0)),
    )

    @Test
    fun createListLoadRenameDuplicateDelete() {
        val store = ProjectStore(File(tmp.root, "projects"))
        assertTrue(store.list().isEmpty())
        val id = store.create(plan)
        assertEquals("Flat", store.load(id)!!.projectName)
        assertTrue(store.rename(id, "Home"))
        val copy = store.duplicate(id, "Home copy")
        assertNotNull(copy)
        assertEquals(setOf("Home", "Home copy"), store.list().map { it.project.projectName }.toSet())
        assertTrue(store.delete(id))
        assertNull(store.load(id))
        assertEquals(1, store.list().size)
    }

    @Test
    fun brokenFilesAreSkipped() {
        val dir = tmp.newFolder("projects")
        File(dir, "bad.json").writeText("not json")
        val store = ProjectStore(dir)
        store.create(plan)
        assertEquals(1, store.list().size)
    }

    @Test
    fun legacyAutosaveIsMigratedOnce() {
        val legacy = tmp.newFile("autosave.json").apply { writeText(ProjectIO.toJson(plan)) }
        val store = ProjectStore(File(tmp.root, "projects"))
        store.migrateLegacy(legacy)
        store.migrateLegacy(legacy)
        assertEquals(1, store.list().size)
        assertTrue(!legacy.exists())
    }
}
