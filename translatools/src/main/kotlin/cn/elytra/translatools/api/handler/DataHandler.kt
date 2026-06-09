package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.TranslationOutputManager
import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.api.utils.ProjectPath
import cn.elytra.translatools.internal.handler.JsonHandler
import java.nio.file.Path

/**
 * ```
 * (Path) ----> (Data) --> (Translation Entries) --+
 * (Path) ----> (Data) ----------------------------+---> (Output)
 * ```
 *
 * @param Data the intermediate form of the data. it is expected to be mutable, so that [injectData] can directly modify the data.
 */
@Deprecated("A new design will be used to replace this.")
public interface DataHandler<Data> : Handler {
    /**
     * Load the content from the file.
     *
     * @param path the path to the file
     */
    public fun loadData(path: Path): Data

    /**
     * From the data, load the key-value map.
     */
    public fun extractData(data: Data): List<TranslationItem>

    /**
     * Load the translated key-value map back to the data.
     */
    public fun injectData(
        data: Data,
        translate: List<TranslationItem>,
    )

    /**
     * Save the translated data.
     *
     * @param output the translation output manager
     */
    context(output: TranslationOutputManager)
    public fun saveData(
        data: Data,
        sourcesPath:
            @PathMarker.Relative(relativeTo = "sourcesDirectory")
            Path,
    )

    override fun extractEntries(context: ExtractContext): Map<ProjectPath, () -> List<TranslationItem>> {
        val result = mutableMapOf<ProjectPath, () -> List<TranslationItem>>()
        context.listDirectoryEntriesByGlobs().forEach { path ->
            result[path] = {
                val data = JsonHandler.loadData(path.absolute)
                JsonHandler.extractData(data)
            }
        }
        return result
    }

    override fun insertEntries(
        context: InsertContext,
        output: TranslationOutputManager,
    ) {
        context.entries.forEach { (path, items) ->
            val data = loadData(path.absolute)
            injectData(data, items)
            context(output) { saveData(data, context.sourcesDirectory) }
        }
    }
}
