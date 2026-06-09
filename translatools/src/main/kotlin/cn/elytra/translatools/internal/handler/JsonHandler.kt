package cn.elytra.translatools.internal.handler

import cn.elytra.translatools.api.TranslationOutputManager
import cn.elytra.translatools.api.handler.DataHandler
import cn.elytra.translatools.api.handler.TranslationItem
import cn.elytra.translatools.internal.utils.removeUTF8Bom
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText

internal object JsonHandler : DataHandler<MutableMap<String, String>> {
    var json: Json = Json

    override fun loadData(path: Path): MutableMap<String, String> = json.decodeFromString(path.readText().removeUTF8Bom())

    override fun extractData(data: MutableMap<String, String>): List<TranslationItem> =
        buildList {
            data.forEach { (key, value) -> this.add(TranslationItem(key, value)) }
        }

    override fun injectData(
        data: MutableMap<String, String>,
        translate: List<TranslationItem>,
    ) {
        val keys = data.keys.toSet()
        // we don't need these untranslated entries
        data.clear()
        // update the data map
        translate.forEach { (key, _, translation) ->
            if (key in keys) data[key] = translation.orEmpty()
        }
    }

    context(output: TranslationOutputManager)
    override fun saveData(
        data: MutableMap<String, String>,
        sourcesPath: Path,
    ) {
        output.addLanguageMap(data)
    }

    override fun getDescription(): String = "Process key-value pair JSON files."

    override fun getManualPages(): String =
        """
        Process key-value pair JSON files.
        The root element must be an Object, whose children must be primitive String.
        
        For example,
        { "foo": "Foo, the Text", "bar": "Bar, the Baron" }
        
        JSON5 is not supported, so no comments are allowed.
        This can be used for KubeJS Assets ('kubejs/assets').
        """.trimIndent()
}
