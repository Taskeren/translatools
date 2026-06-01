package cn.elytra.translatools.cursefetch.api

import cn.elytra.translatools.cursefetch.api.exception.NetworkFailureException
import io.ktor.client.*
import io.ktor.client.content.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Download the content at the given url.
 */
suspend fun HttpClient.downloadTo(
    url: String,
    dest: Path,
    progressListener: ProgressListener? = null,
    // FIXME: the CDN responds 404 with Range header. we must get the real link (not redirect) to send a 'Range'-d request.
    allowAppend: Boolean = false, // buggy, don't use!!
    requestTimeout: Duration? = null,
    socketTimeout: Duration = 30.seconds,
) {
    // get the size of the existing file,
    // and trying to continue instead of downloading from nothing again.
    val downloadedBytes = if (dest.exists() && allowAppend) dest.fileSize() else 0

    prepareGet(url) {
        // the header that tells the server where should we continue
        if (downloadedBytes > 0) {
            header("Range", "bytes=$downloadedBytes-")
        }
        // overwrite the timeout
        timeout {
            // infinite download time in case of bad network
            requestTimeoutMillis = requestTimeout?.toLong(DurationUnit.MILLISECONDS) ?: Long.MAX_VALUE
            // keep the connection as long as server sends something in 30s
            socketTimeoutMillis = socketTimeout.toLong(DurationUnit.MILLISECONDS)
        }
        // attach progress listener if capable
        onDownload(progressListener)
    }.execute { resp ->
        // check failure
        if (!resp.status.isSuccess()) {
            throw NetworkFailureException(resp)
        }
        // check if the server responds to the Range header
        val isPartial = resp.status == HttpStatusCode.PartialContent
        val append = isPartial && downloadedBytes > 0

        val chan = resp.bodyAsChannel()
        // open an append output stream if capable
        val outputStream = if (append) dest.outputStream(StandardOpenOption.APPEND) else dest.outputStream()
        outputStream.use { outputStream ->
            chan.copyTo(outputStream)
        }
    }
}
