package cn.elytra.translatools.internal.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

internal abstract class OneOrMoreSerializer<T>(
    serializer: KSerializer<T>,
) : JsonTransformingSerializer<List<T>>(ListSerializer(serializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement = element as? JsonArray ?: JsonArray(listOf(element))

    class StringSerializer : OneOrMoreSerializer<String>(String.serializer())
}
