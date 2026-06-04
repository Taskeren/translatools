package cn.elytra.translatools

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.useLines

internal fun loadEnvAsMap(path: Path = Path(".env")): Map<String, String> =
    buildMap {
        // load normal environments
        System.getenv().forEach { (key, value) ->
            this[key] = value
        }

        // load local file
        if (path.exists()) {
            path.useLines { lines ->
                lines
                    .map { it.trim() }
                    .filterNot { it.startsWith("#") }
                    .forEach { line ->
                        val split = line.split('=', limit = 2)
                        val key = split.getOrElse(0) { "" }
                        val value = split.getOrElse(1) { "" }
                        this[key] = value
                    }
            }
        }
    }
