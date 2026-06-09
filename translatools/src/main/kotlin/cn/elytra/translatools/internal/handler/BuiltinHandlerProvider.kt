package cn.elytra.translatools.internal.handler

import cn.elytra.translatools.api.HandlerProvider
import cn.elytra.translatools.api.handler.Handler

internal class BuiltinHandlerProvider : HandlerProvider {
    override fun provide(): Map<String, Handler> =
        mapOf(
            "json" to JsonHandler,
            "snbt" to SnbtHandler,
            "ftb-quests-forcibly-translated" to FTBQuestsForciblyTranslatedHandler,
        )

    // lower the builtin ones, so that others may override them
    override fun priority(): Int = -100
}
