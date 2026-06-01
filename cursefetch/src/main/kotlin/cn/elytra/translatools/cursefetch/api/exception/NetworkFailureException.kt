package cn.elytra.translatools.cursefetch.api.exception

import io.ktor.client.statement.*

class NetworkFailureException(
    response: HttpResponse,
) : ApiException(response, response.status.toString())
