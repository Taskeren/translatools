package cn.elytra.translatools.api.utils

import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.internal.utils.walkGlob
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString
import kotlin.io.path.readLines

public data class ProjectPath(
    val absolute: @PathMarker.Absolute Path,
    val relative: @PathMarker.Relative Path,
) {
    init {
        require(absolute.isAbsolute) { "Absolute path must be absolute." }
        require(!relative.isAbsolute) { "Relative path must be relative." }
    }

    public constructor(path: Path) : this(path, Path(""))

    private val common: Path
        get() {
            if (relative.pathString == "") return absolute
            var p: Path? = absolute
            repeat(relative.nameCount) {
                p = p?.parent
            }
            return p ?: Path(".")
        }

    public fun resolve(path: Path): ProjectPath = ProjectPath(absolute / path, relative / path)

    public fun resolve(path: String): ProjectPath = ProjectPath(absolute / path, relative / path)

    public operator fun div(path: Path): ProjectPath = resolve(path)

    public operator fun div(path: String): ProjectPath = resolve(path)

    public fun walkGlob(glob: String = "*"): Sequence<ProjectPath> =
        absolute.walkGlob(glob).map { absolute ->
            ProjectPath(absolute, this.common.relativize(absolute))
        }
}

public fun ProjectPath.readLines(charset: Charset = Charsets.UTF_8): List<String> = absolute.readLines(charset)
