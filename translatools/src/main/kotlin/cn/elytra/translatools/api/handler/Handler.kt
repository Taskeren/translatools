package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.TranslationOutputManager
import cn.elytra.translatools.api.utils.ProjectPath
import cn.elytra.translatools.internal.HandlerRegistration
import kotlinx.serialization.json.Json

public interface Handler {
    public fun extractEntries(context: ExtractContext): Map<ProjectPath, () -> List<TranslationItem>>

    public fun insertEntries(
        context: InsertContext,
        output: TranslationOutputManager,
    )

    public fun getDescription(): String? = null

    public fun getManualPages(): String? = null

    public companion object {
        /**
         * Get a handler by the name.
         */
        public fun getByName(name: String): Handler? = HandlerRegistration.getByName(name)

        /**
         * Register a handler.
         *
         * @see cn.elytra.translatools.api.HandlerProvider
         */
        public fun register(
            name: String,
            handler: Handler,
        ): Unit = HandlerRegistration.register(name, handler)

        public fun listAll(): Map<String, Handler> = HandlerRegistration.listAll()

        // FIXME: move somewhere else
        public inline fun <E> lazyBuildList(crossinline block: MutableList<E>.() -> Unit): () -> List<E> = { buildList(block) }
    }
}

internal fun Handler.extractAndEncodeToJson(
    context: ExtractContext,
    json: Json = Json,
): Map<ProjectPath, () -> String> =
    extractEntries(context).mapValues { (_, provider) ->
        {
            val value = provider()
            json.encodeToString(value)
        }
    }
