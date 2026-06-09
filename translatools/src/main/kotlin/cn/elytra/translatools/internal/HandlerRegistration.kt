package cn.elytra.translatools.internal

import cn.elytra.translatools.api.HandlerProvider
import cn.elytra.translatools.api.handler.Handler
import org.slf4j.LoggerFactory
import java.util.*

/**
 * The registration of [Handler].
 */
internal object HandlerRegistration {
    private val log = LoggerFactory.getLogger(HandlerRegistration::class.java)
    private val handlers: MutableMap<String, Handler> = mutableMapOf()

    init {
        // load providers
        ServiceLoader
            .load(HandlerProvider::class.java, HandlerProvider::class.java.classLoader)
            .iterator()
            .asSequence()
            .sortedBy { it.priority() }
            .forEach { provider ->
                provider.provide().forEach { (key, value) ->
                    if (!contains(key)) {
                        register(key, value)
                        log.debug("Registered {} ({})", key, value::class.qualifiedName)
                    } else {
                        log.debug("Ignored {} ({}) because of ID duplication", key, value::class.qualifiedName)
                    }
                }
            }
    }

    fun contains(name: String) = handlers.containsKey(name)

    fun register(
        name: String,
        handler: Handler,
    ) {
        if (handlers.containsKey(name)) {
            error("Handler $name already registered!")
        }
        handlers[name] = handler
    }

    fun getByName(name: String): Handler? = handlers[name]

    fun listAll(): Map<String, Handler> = handlers.toMap()
}
