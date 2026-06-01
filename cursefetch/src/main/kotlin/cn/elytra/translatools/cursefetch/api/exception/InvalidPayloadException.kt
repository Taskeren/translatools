package cn.elytra.translatools.cursefetch.api.exception

import io.ktor.client.statement.*

class InvalidPayloadException(
    response: HttpResponse,
    cause: Exception,
) : ApiException(response, cause)
