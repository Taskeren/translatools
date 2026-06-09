package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.api.project.Project
import cn.elytra.translatools.api.utils.ProjectPath
import java.nio.file.Path

public data class InsertContext(
    public val project: Project,
    public val entries: Map<ProjectPath, List<TranslationItem>>,
) {
    public val sourcesDirectory: @PathMarker.Absolute Path get() = project.sourcesDirectory
}
