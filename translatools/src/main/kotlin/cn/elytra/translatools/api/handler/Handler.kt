package cn.elytra.translatools.api.handler

import cn.elytra.translatools.internal.HandlerRegistration
import java.nio.file.Path

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
    public fun saveData(data: Data)

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

internal fun <T> Handler<T>.extractUntranslated(path: Path): List<TranslationItem> {
    val data = loadData(path)
    return extractData(data)
}

context(_: TranslationOutputManager)
internal fun <T> Handler<T>.assembleTranslated(
    path: Path,
    translate: List<TranslationItem>,
) {
    val data = loadData(path)
    injectData(data, translate)
    saveData(data)
}
