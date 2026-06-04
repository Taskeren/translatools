package cn.elytra.translatools.api.handler

import kotlinx.serialization.Serializable

@Serializable
public data class TranslationItem(
    val key: String,
    var original: String,
    var translation: String? = null,
    var context: String? = null,
)
