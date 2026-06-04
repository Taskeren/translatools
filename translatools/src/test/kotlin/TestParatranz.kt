package cn.elytra.translatools

import cn.elytra.translatools.internal.utils.Checksum
import cn.elytra.translatools.internal.platform.Paratranz
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TestParatranz {
    // https://paratranz.cn/projects/18549
    val projectId = 18549

    @Test // bridge function
    fun testParatranz() = runBlocking { testParatranzSuspend() }

    private suspend fun testParatranzSuspend() {
        val token = System.getenv("PARATRANZ_TOKEN")
        if (token.isNullOrEmpty()) {
            println("PARATRANZ_TOKEN is not set, skipping tests")
            return
        }

        val httpClient =
            HttpClient {
                install(Logging) {
                    level = LogLevel.ALL
                }
            }
        val paratranz = Paratranz(token, httpClient)

        val path = Path("data.json")
        path.writeText(getJsonContent(0))
        // make sure it's deleted on exit
        path.toFile().deleteOnExit()

        try {
            // check if there's a leftover 'data.json',
            // delete it first if so.
            val list = paratranz.getFileList(projectId)
            val file = list.find { it.name == "data.json" }
            if (file != null) {
                println("Deleting existing 'data.json'")
                paratranz.deleteFile(projectId, file.id)
            }
        } catch (e: Exception) {
            println("Failed to check/delete existing 'data.json'")
            println("The exception is ignored, but it may cause problems later.")
            e.printStackTrace()
        }

        val fileId: Int
        val expectedFileMetadata: Paratranz.File
        // create
        try {
            val (file, _) = paratranz.uploadFile(projectId, path)
            assertEquals("data.json", file.name, "Uploaded file is not named 'data.json'")
            assertEquals("", file.folder, "Uploaded file is not located in empty-path folder")
            val hash = Checksum.md5(path).toHexString()
            assertEquals(hash, file.hash, "Uploaded file has a wrong hash")
            assertEquals(projectId, file.project, "Uploaded file has a wrong projectId")
            fileId = file.id
            expectedFileMetadata = file
        } catch (e: Exception) {
            error("Failed to upload file '$path'", e)
        }

        // get the information again
        try {
            val file = paratranz.getFileMetadata(projectId, fileId)
            assertEquals(expectedFileMetadata, file, "Re-requested file metadata mismatches")
        } catch (e: Exception) {
            error("Failed to get file metadata '$path'", e)
        }

        // update untranslated
        path.writeText(getJsonContent(1))
        try {
            val (file, _) = paratranz.updateFile(projectId, fileId, path)
            assertEquals(fileId, file.id, "Updated file has a wrong ID")
        } catch (e: Exception) {
            error("Failed to update the file '$path'", e)
        }

        // delete
        try {
            paratranz.deleteFile(projectId, fileId)
        } catch (e: Exception) {
            error("Failed to delete file '$path'", e)
        }
    }
}

private fun error(
    message: String,
    cause: Throwable,
): Nothing = throw NoStackTraceException(message, cause)

private fun getJsonContent(version: Int): String {
    val data =
        mutableMapOf<String, String>().apply {
            // initial version (0)
            this["hello"] = "Hello"
            this["hello_world"] = "HELLO WORLD"
            this["foo"] = "foo"
            if (version == 0) return@apply
            // version 1, foo -> Foolish!
            this["foo"] = "Foolish!"
            if (version == 1) return@apply
        }
    return Json.encodeToString(data)
}
