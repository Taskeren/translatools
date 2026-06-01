package cn.elytra.translatools.cursefetch.internal

internal val defaultApiKey: String? by lazy { loadApiKey() }

private fun loadApiKey(): String? =
    System.getenv("CURSEFETCH_API_KEY")
        ?: System.getProperty("cursefetch.apikey")
