package cn.elytra.translatools.api.handler

import cn.elytra.translatools.api.project.MissingHandlerException
import kotlinx.serialization.Serializable

/**
 * A checked handler name that must exist.
 */
@JvmInline
@Serializable
public value class HandlerName(
    public val name: String,
) {
    init {
        // check if the handler exists
        if (Handler.getByName(name) == null) throw MissingHandlerException(name)
    }

    public fun get(): Handler<*> = Handler.getByName(name) ?: throw MissingHandlerException(name)

    // @JvmName can't be used on top of this.
    public inline fun <reified T> getAs(): Handler<T> = Handler.getByName<T>(name) ?: throw MissingHandlerException(name)
}
