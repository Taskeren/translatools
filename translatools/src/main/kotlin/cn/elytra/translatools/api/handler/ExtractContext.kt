package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.project.Project
import cn.elytra.translatools.api.utils.ProjectPath
import kotlinx.serialization.json.JsonObject

public data class ExtractContext(
    public val project: Project,
    public val globs: List<String>,
    public val with: JsonObject? = null,
) {
    public val sourcesDirectory: ProjectPath get() = ProjectPath(project.sourcesDirectory)

    public fun listDirectoryEntriesByGlobs(): List<ProjectPath> = globs.flatMap { sourcesDirectory.walkGlob(it) }
}
