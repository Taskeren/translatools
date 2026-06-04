package cn.elytra.translatools.internal.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.Contract
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal inline fun <reified T> Json.encodeToStringToPath(
    value: T,
    path: Path,
    charset: Charset = Charsets.UTF_8,
) = path.writeText(encodeToString<T>(value), charset)

internal inline fun <reified T> Json.decodeFromStringFromPath(
    path: Path,
    charset: Charset = Charsets.UTF_8,
): T = decodeFromString<T>(path.readText(charset))

/**
 * Run the [block] in parallel for an iteration.
 */
internal suspend inline fun <T> Iterable<T>.forEachCoroutineScope(crossinline block: suspend CoroutineScope.(T) -> Unit) =
    supervisorScope {
        forEach { el ->
            launch { block(el) }
        }
    }

/**
 * Run the [block] in parallel for an iteration.
 */
internal suspend inline fun <K, V> Map<out K, V>.forEachCoroutineScope(
    crossinline block: suspend CoroutineScope.(Map.Entry<K, V>) -> Unit,
) = supervisorScope {
    forEach { el ->
        launch { block(el) }
    }
}

/**
 * Remove the leading BOM character, so that JSON parser won't panic.
 */
internal fun String.removeUTF8Bom(): String = trimStart('\uFEFF')

/**
 * Grouping the entries by the selector.
 */
@Contract(pure = true)
internal fun <K, V, T> Map<K, V>.groupEntriesBy(selector: (Map.Entry<K, V>) -> T): Map<T, Map<K, V>> =
    entries
        .groupBy<Map.Entry<K, V>, T> { selector(it) }
        .mapValues { (_, entries) -> entries.associate { (key, value) -> key to value } }
