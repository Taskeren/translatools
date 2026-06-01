package cn.elytra.translatools.cursefetch.api.exception

import io.ktor.client.statement.*

sealed class ApiException : Exception {
    val response: HttpResponse

    constructor(response: HttpResponse, message: String) : super(message) {
        this.response = response
    }

    constructor(response: HttpResponse, cause: Exception) : super(cause) {
        this.response = response
    }
}
