package cn.elytra.translatools.api.project

import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.api.handler.HandlerName
import cn.elytra.translatools.internal.utils.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.nio.file.Path
import kotlin.io.path.Path

@Serializable
public data class ProjectConfig(
    val name: String = "unnamed",
    @Serializable(with = PathSerializer::class)
    val root:
        @PathMarker.Relative(relativeTo = "parent of this configuration file")
        Path = Path("overrides"),
    val handlers: List<FileHandler> = emptyList(),
    val paratranz: ParatranzConfig? = null,
)

@Serializable
public data class FileHandler(
    val uses: HandlerName,
    val glob: String,
    val with: JsonObject? = null,
)

@Serializable
public data class ParatranzConfig(
    val projectId: Int,
)
