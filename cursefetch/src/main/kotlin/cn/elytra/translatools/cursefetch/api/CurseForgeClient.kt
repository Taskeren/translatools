package cn.elytra.translatools.cursefetch.api

import cn.elytra.translatools.cursefetch.api.exception.InvalidPayloadException
import cn.elytra.translatools.cursefetch.api.exception.NetworkFailureException
import cn.elytra.translatools.cursefetch.internal.buildApiUrl
import cn.elytra.translatools.cursefetch.internal.defaultApiKey
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus

class CurseForgeClient(
    httpClient: HttpClient,
) {
    /**
     * The API key.
     *
     * API-note: you should get your own API key for using their APIs.
     * An application can be found [here](https://support.curseforge.com/support/solutions/articles/9000208346-about-the-curseforge-api-and-how-to-apply-for-a-key).
     * And they hide their API documents deep inside the world [here](https://docs.curseforge.com/rest-api/#getting-started).
     */
    var apiKey: String = ""
        get() {
            return field.takeIf { it.isNotEmpty() } ?: defaultApiKey ?: error("API key not set")
        }

    private val httpClient =
        httpClient.config {
            defaultRequest {
                // inject headers
                header("X-API-Key", apiKey)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    @ApiStatus.Internal
    fun getHttpClient() = httpClient

    private suspend inline fun <reified T> HttpResponse.checkAndBody(): Result<T> =
        if (status.isSuccess()) {
            try {
                return Result.success(body())
            } catch (e: Exception) {
                Result.failure(InvalidPayloadException(this, e))
            }
        } else {
            Result.failure(NetworkFailureException(this))
        }

    /**
     * Get a list of files under the given project (mods in most cases).
     */
    suspend fun getModFiles(
        modId: Int,
        pageSize: Int = 50,
        index: Int = 0,
    ): Result<GetModFilesResponse> {
        // 50 at most
        val pageSize = pageSize.coerceAtMost(50)
        return httpClient
            .get(
                buildApiUrl(
                    "/mods/$modId/files",
                    "pageSize" to pageSize,
                    "index" to index,
                ),
            ).checkAndBody()
    }

    /**
     * Get lists of files under the given project. If the response is incomplete, the flow will continue to request.
     */
    fun getModFilesAsFlow(
        modId: Int,
        pageSizePerRequest: Int = 50,
    ): Flow<List<CurseForge.File>> =
        flow {
            val pageSizePerRequest = pageSizePerRequest.coerceAtMost(50)
            var hasMore: Boolean
            var index = 0
            do {
                val response = getModFiles(modId, pageSizePerRequest, index).getOrThrow()
                emit(response.data)
                hasMore = response.pagination.resultCount >= pageSizePerRequest
                index += response.pagination.resultCount
            } while (hasMore)
        }

    suspend fun getModFile(
        modId: Int,
        fileId: Int,
    ): Result<GetModFileResponse> = httpClient.get(buildApiUrl("/mods/$modId/files/$fileId")).checkAndBody()
}
