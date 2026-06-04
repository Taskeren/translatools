// weird behavior of Kotlin plugin that a modifier is required for constructors of sealed classes.
@file:Suppress("RedundantVisibilityModifier")

package cn.elytra.translatools.api.project

import cn.elytra.translatools.api.annotation.PathMarker
import java.nio.file.Path

public sealed class ProjectException : Exception {
    protected constructor(message: String?) : super(message)
    protected constructor(message: String?, cause: Throwable?) : super(message, cause)
    protected constructor(cause: Throwable?) : super(cause)
    protected constructor(
        message: String?,
        cause: Throwable?,
        enableSuppression: Boolean,
        writableStackTrace: Boolean,
    ) : super(
        message,
        cause,
        enableSuppression,
        writableStackTrace,
    )
}

public class ProjectInternalException(
    message: String,
) : ProjectException(message)

public fun ProjectException(message: String): ProjectException = ProjectInternalException(message)

public class MissingHandlerException(
    public val handler: String,
) : ProjectException("Required handler $handler was missing.")

public class ProjectConfigException(
    message: String,
) : ProjectException(message)

public sealed class IndexingException : ProjectException {
    protected constructor(message: String?) : super(message)
    protected constructor(message: String?, cause: Throwable?) : super(message, cause)
    protected constructor(cause: Throwable?) : super(cause)
    protected constructor(
        message: String?,
        cause: Throwable?,
        enableSuppression: Boolean,
        writableStackTrace: Boolean,
    ) : super(
        message,
        cause,
        enableSuppression,
        writableStackTrace,
    )
}

/**
 * @property handler the current handler that tries to index the file the 2nd time.
 */
public class DoubleIndexedFileException(
    public val path: @PathMarker.Absolute Path,
    public val handler: String,
) : IndexingException("Path $path is indexed by multiple handlers.")

public class ParatranzSyncException(
    public val path: Path,
    cause: Throwable,
) : ProjectException("Failed to synchronize path $path", cause)
