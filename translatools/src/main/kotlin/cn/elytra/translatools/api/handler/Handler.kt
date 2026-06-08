package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.annotation.ExperimentalAPI
import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.internal.HandlerRegistration
import java.nio.file.Path
import kotlin.io.path.div

/**
 * The handler that describes how to handle a type of data.
 *
 * @param Data the intermediate form of the data. it is expected to be mutable, so that [injectData] can directly modify the data.
 */
public interface Handler<Data> {
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

    public fun getDescription(): String? = null

    public fun getManualPages(): String? = null

    public companion object {
        /**
         * Get a handler by the name.
         */
        public fun getByName(name: String): Handler<*>? = HandlerRegistration.getByName(name)

        /**
         * Get a handler by the name.
         *
         * @param T the type of the handler. not checked.
         */
        @JvmName("+getByName")
        public inline fun <reified T> getByName(name: String): Handler<T>? = HandlerRegistration.getByName<T>(name)

        /**
         * Register a handler.
         *
         * @see cn.elytra.translatools.api.HandlerProvider
         */
        public fun register(
            name: String,
            handler: Handler<*>,
        ): Unit = HandlerRegistration.register(name, handler)

        public fun listAll(): Map<String, Handler<*>> = HandlerRegistration.listAll()
    }
}

@ExperimentalAPI
public interface ConfigurableHandler<Data, Config> : Handler<Data> {
    public fun defaultConfig(): Config

    public fun loadData(
        path: Path,
        config: Config,
    ): Data

    public fun extractData(
        data: Data,
        config: Config,
    ): List<TranslationItem>

    public fun injectData(
        data: Data,
        translate: List<TranslationItem>,
        config: Config,
    )

    context(output: TranslationOutputManager)
    public fun saveData(
        data: Data,
        sourcesPath:
            @PathMarker.Relative(relativeTo = "sourcesDirectory")
            Path,
        config: Config,
    )

    override fun loadData(path: Path): Data = loadData(path, defaultConfig())

    override fun extractData(data: Data): List<TranslationItem> = extractData(data, defaultConfig())

    override fun injectData(
        data: Data,
        translate: List<TranslationItem>,
    ) {
        injectData(data, translate, defaultConfig())
    }

    context(output: TranslationOutputManager)
    override fun saveData(
        data: Data,
        sourcesPath:
            @PathMarker.Relative(relativeTo = "sourcesDirectory")
            Path,
    ) {
        saveData(data, sourcesPath, defaultConfig())
    }
}

internal fun <T> Handler<T>.extractUntranslated(path: Path): List<TranslationItem> {
    val data = loadData(path)
    return extractData(data)
}

context(_: TranslationOutputManager)
internal fun <T> Handler<T>.assembleTranslated(
    sourcesDirectory: @PathMarker.Absolute Path,
    path:
        @PathMarker.Relative(relativeTo = "sourcesDirectory")
        Path,
    translate: List<TranslationItem>,
) {
    val data = loadData(sourcesDirectory / path)
    injectData(data, translate)
    saveData(data, path)
}
