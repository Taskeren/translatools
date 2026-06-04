package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.annotation.PathMarker.Relative
import cn.elytra.translatools.internal.SharedObjects
import io.ktor.utils.io.charsets.Charset
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.*
import kotlin.text.Charsets

public class TranslationOutputManager(
    public val translationDirectory: Path,
    public val duplicatedKeyStrategy: DuplicatedKeyStrategy = DuplicatedKeyStrategy.PANIC,
) {
    private val json = SharedObjects.encodingJson
    private val languageMap: MutableMap<String, String> = ConcurrentHashMap()
    private val delayedContent: MutableMap<
        @Relative("translationDirectory")
        Path,
        () -> ByteArray,
    > = ConcurrentHashMap()

    init {
        // add language map as delayed
        addDelayedContent(Path("kubejs/assets/translatools/lang/zh_cn.json")) {
            json.encodeToString(languageMap).toByteArray(Charsets.UTF_8)
        }
    }

    /**
     * Add a language entry.
     */
    public fun addLanguage(
        key: String,
        value: String,
    ) {
        val old = languageMap[key]
        languageMap[key] = if (old != null) duplicatedKeyStrategy.determineValue(old, value, key) else value
    }

    public fun addLanguageMap(data: Map<String, String>) {
        data.forEach { (key, value) -> addLanguage(key, value) }
    }

    public fun addContent(
        path:
            @Relative("translationDirectory")
            Path,
        value: String,
        charset: Charset = Charsets.UTF_8,
    ) {
        check(path.isRelative) { "Path $path must be relative to the translation directory" }
        (translationDirectory / path).parentMkdirs().writeText(value, charset)
    }

    public fun addBinaryContent(
        path:
            @Relative("translationDirectory")
            Path,
        byteArray: ByteArray,
    ) {
        check(path.isRelative) { "Path $path must be relative to the translation directory" }
        (translationDirectory / path).parentMkdirs().writeBytes(byteArray)
    }

    public fun addDelayedContent(
        path:
            @Relative("translationDirectory")
            Path,
        provider: () -> ByteArray,
    ) {
        check(path.isRelative) { "Path $path must be relative to the translation directory" }
        delayedContent[path] = provider
    }

    internal fun executeDelayed() {
        // get and clear the map
        val delayedContent = delayedContent.toMap()
        this.delayedContent.clear()

        delayedContent.forEach { (path, provider) ->
            val byteArray = provider()
            addBinaryContent(path, byteArray)
        }
    }
}

private val Path.isRelative: Boolean get() = !isAbsolute

private fun Path.parentMkdirs() = apply { parent.createDirectories() }
