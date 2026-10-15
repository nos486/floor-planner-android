package org.geekio.floorplanner.core

import org.geekio.floorplanner.model.Project
import org.geekio.floorplanner.model.SavedVersion
import java.io.File

/** A saved project as shown on the start screen. */
data class ProjectEntry(val id: String, val project: Project, val modified: Long)

/** Keeps every project as `<id>.json` in [dir] (the app's private storage). */
class ProjectStore(private val dir: File) {
    private fun file(id: String) = File(dir, "$id.json")

    fun list(): List<ProjectEntry> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { f ->
            val project = runCatching { f.readText() }.getOrNull()?.let { ProjectIO.fromJson(it) } ?: return@mapNotNull null
            ProjectEntry(f.name.removeSuffix(".json"), project, f.lastModified())
        }.sortedByDescending { it.modified }
    }

    fun load(id: String): Project? = file(id).takeIf { it.exists() }
        ?.let { runCatching { it.readText() }.getOrNull() }
        ?.let { ProjectIO.fromJson(it) }

    fun save(id: String, project: Project): Boolean = runCatching {
        dir.mkdirs()
        // Write to a temporary file first so a crash never leaves a half-written project.
        val tmp = File(dir, "$id.json.tmp")
        tmp.writeText(ProjectIO.toJson(project))
        if (!tmp.renameTo(file(id))) {
            file(id).writeText(tmp.readText())
            tmp.delete()
        }
    }.isSuccess

    /** Saves [project] under a new id and returns it. */
    fun create(project: Project): String {
        val id = generateId("p")
        save(id, project)
        return id
    }

    /** The photo under the plan of project [id], if it has one. */
    fun backgroundFile(id: String) = File(dir, "$id.bg.jpg")

    /** A saved version of a project: [key] names its file. */
    data class VersionEntry(val key: String, val name: String, val time: Long)

    private fun versionDir(id: String) = File(dir, "$id.versions")

    fun saveVersion(id: String, name: String, project: Project, time: Long = System.currentTimeMillis()): Boolean = runCatching {
        val d = versionDir(id).apply { mkdirs() }
        File(d, "$time.json").writeText(ProjectIO.versionToJson(SavedVersion(name, time, project)))
    }.isSuccess

    /** Saved versions of project [id], newest first. */
    fun versions(id: String): List<VersionEntry> =
        (versionDir(id).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).mapNotNull { f ->
            ProjectIO.versionFromJson(runCatching { f.readText() }.getOrNull() ?: return@mapNotNull null)
                ?.let { VersionEntry(f.name.removeSuffix(".json"), it.name, it.time) }
        }.sortedByDescending { it.time }

    fun loadVersion(id: String, key: String): Project? =
        runCatching { File(versionDir(id), "$key.json").readText() }.getOrNull()?.let { ProjectIO.versionFromJson(it)?.project }

    fun deleteVersion(id: String, key: String) = File(versionDir(id), "$key.json").delete()

    fun delete(id: String): Boolean {
        backgroundFile(id).delete()
        versionDir(id).deleteRecursively()
        return file(id).delete()
    }

    fun duplicate(id: String, name: String): String? = load(id)?.let { p ->
        create(p.copy(projectName = name)).also { copy ->
            backgroundFile(id).takeIf { it.exists() }?.let { runCatching { it.copyTo(backgroundFile(copy), overwrite = true) } }
        }
    }

    fun rename(id: String, name: String): Boolean {
        val p = load(id) ?: return false
        return save(id, p.copy(projectName = name.trim().ifEmpty { p.projectName }))
    }

    /** Moves the single-plan autosave of older versions into the store, once. */
    fun migrateLegacy(legacy: File) {
        if (!legacy.exists()) return
        val p = runCatching { legacy.readText() }.getOrNull()?.let { ProjectIO.fromJson(it) }
        if (p != null && (p.walls.isNotEmpty() || p.items.isNotEmpty())) create(p)
        legacy.delete()
    }
}
