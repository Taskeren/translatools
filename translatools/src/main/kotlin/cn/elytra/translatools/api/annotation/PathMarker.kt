package cn.elytra.translatools.api.annotation

import java.nio.file.Path

/**
 * The annotation markers for [Path].
 */
public object PathMarker {
    /**
     * The path marked with this annotation is expected to be absolute, which is computer-specific.
     *
     * Note: [Path.resolve] will result in another absolute path.
     */
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.TYPE)
    public annotation class Absolute

    /**
     * The path marked with this annotation is expected to be relative to an absolute path property, documented by [relativeTo].
     */
    @Retention(AnnotationRetention.SOURCE)
    @Target(AnnotationTarget.TYPE)
    public annotation class Relative(
        val relativeTo: String = "",
    )
}
