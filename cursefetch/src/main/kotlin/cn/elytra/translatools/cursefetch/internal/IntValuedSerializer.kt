package cn.elytra.translatools.cursefetch.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KProperty1

/**
 * Serializer for an object that can be serde-d into and from an integer.
 *
 * Used for int valued enum classes.
 *
 * Impl-note: this class is `public` for the API entities, don't use this for your project.
 */
abstract class IntValuedSerializer<T>(
    val toInt: (T) -> Int,
    val fromInt: (Int) -> T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("IntValuedSerializer", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: T,
    ) = encoder.encodeInt(toInt(value))

    override fun deserialize(decoder: Decoder): T = fromInt(decoder.decodeInt())
}

/**
 * Find the first element with specific value in the given enum.
 *
 * Impl-note: `Foo::bar` returns a `KProperty<Foo, typeof bar>`, and by constrain the type of Foo as an enum, we can use [enumValues] to iterate it.
 * Usages would be like `Foo::bar.fromInt(1)`, which matches the first element in `Foo` whose `bar` is **1**.
 */
internal inline fun <reified T : Enum<T>> KProperty1<T, Int>.parseInt(selector: Int) =
    enumValues<T>().find { value -> this.get(value) == selector } ?: throw NoSuchElementException()
