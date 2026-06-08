package cn.elytra.translatools.internal.handler

import cn.elytra.translatools.api.handler.Handler
import cn.elytra.translatools.api.handler.TranslationItem
import cn.elytra.translatools.api.handler.TranslationOutputManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.readLines

@ApiStatus.Experimental
internal object LangHandler : Handler<MutableList<LanguageLine>> {
    override fun loadData(path: Path): MutableList<LanguageLine> =
        path.readLines().mapIndexedTo(mutableListOf()) { index, line ->
            val line = line.trim()
            if (line.isEmpty()) {
                EmptyLine
            } else if (line.startsWith('#')) {
                CommentLine(line)
            } else {
                val split = line.split('=', limit = 2)
                val key = split.getOrNull(1)
                val value = split.getOrElse(2) { "" }

                if (key != null) {
                    KeyValueLine(key, value)
                } else {
                    error("Unable to parse line #${index + 1} (\"$line\")")
                }
            }
        }

    override fun extractData(data: MutableList<LanguageLine>): List<TranslationItem> =
        data.filterIsInstance<KeyValueLine>().map { (key, value) -> TranslationItem(key, value) }

    override fun injectData(
        data: MutableList<LanguageLine>,
        translate: List<TranslationItem>,
    ) {
        translate.forEach { (key, value) ->
            data.filterIsInstance<KeyValueLine>().find { it.key == key }?.value = value
        }
    }

    context(output: TranslationOutputManager)
    override fun saveData(data: MutableList<LanguageLine>, sourcesPath: Path) {
        output.addLanguageMap(data.filterIsInstance<KeyValueLine>().associate { (key, value) -> key to value })
    }

    override fun getDescription(): String = "Process legacy Minecraft language files."

    override fun getManualPages(): String =
        """
        Process legacy Minecraft language files.
        
        Comments are allowed at the beginning of the line, but not at the end.
        """.trimIndent()
}

internal sealed interface LanguageLine

private object EmptyLine : LanguageLine

private data class CommentLine(
    val line: String,
) : LanguageLine

private data class KeyValueLine(
    val key: String,
    var value: String,
) : LanguageLine
