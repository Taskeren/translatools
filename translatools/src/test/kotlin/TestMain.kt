package cn.elytra.translatools

import cn.elytra.translatools.internal.platform.Paratranz
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.path.Path
import kotlin.io.path.writeText

suspend fun main() {
    val httpClient =
        HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }

    val p = Paratranz("0878adfeff242add064169f023b0fb86", httpClient)

    val path = Path("./test.json")
    path.writeText(
        """
        {
            "hello": "world"
        }
        """.trimIndent(),
    )
    // make sure it's deleted on exit
    path.toFile().deleteOnExit()

    // val res = p.uploadFile(18549, path)
    val projectId = 18549
    val res = p.getFileTranslation(projectId, 3082340)
    println(res)
}
