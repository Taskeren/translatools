package cn.elytra.translatools.cursefetch.internal

import io.ktor.http.*

private const val URL_BASE = "https://api.curseforge.com/v1"

/**
 * Make an [Url] instance for CurseForge api calls.
 */
internal fun buildApiUrl(
    endpoint: String,
    vararg queries: Pair<String, Any>,
): Url =
    buildUrl {
        takeFrom("$URL_BASE$endpoint")
        queries.forEach { (pathKey, value) ->
            parameters[pathKey] = value.toString()
        }
    }
