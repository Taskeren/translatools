package cn.elytra.translatools.internal

import kotlinx.serialization.json.Json

internal object SharedObjects {
    val encodingJson =
        Json {
            // don't discard default values when encoding
            encodeDefaults = true
            // pretty print
            prettyPrint = true
        }

    val decodingJson =
        Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
}
