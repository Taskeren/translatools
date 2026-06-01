package cn.elytra.translatools.cursefetch

import cn.elytra.translatools.cursefetch.api.CurseForgeClient
import cn.elytra.translatools.cursefetch.api.downloadTo
import io.ktor.client.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlin.io.path.Path

suspend fun main() {
    val httpClient = HttpClient()
    val api = CurseForgeClient(httpClient)

    val f =
        api
            .getModFilesAsFlow(925200, pageSizePerRequest = 10)
            .take(1)
            .first()
            .first()
    val url = f.downloadUrl
    println("Downloading $url")

    httpClient.downloadTo(
        url,
        Path("[download_test]${f.fileName}"),
        suspend { sent, total ->
            if (total != null && total != 0L) {
                val percent = sent.toDouble() * 100.0 / total
                System.out.printf("\rDownloaded %.2f%% | %s of %s%n", percent, sent, total)
            } else {
                System.out.printf("\rDownloaded %s bytes%n", sent)
            }
        },
    )
}
