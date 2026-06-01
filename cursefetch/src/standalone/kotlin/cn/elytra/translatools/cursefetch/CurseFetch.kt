package cn.elytra.translatools.cursefetch

import cn.elytra.translatools.cursefetch.api.CurseForgeClient
import cn.elytra.translatools.cursefetch.api.downloadTo
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import io.ktor.client.*
import kotlinx.coroutines.flow.take
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.time.measureTime

private val client by lazy {
    createClient()
}

private fun createClient(): CurseForgeClient {
    val client = CurseForgeClient(HttpClient())
    // load curseforge key
    val key = Path(".cursefetch_key")
    if (key.exists()) client.apiKey = key.readText()
    return client
}

suspend fun main(args: Array<String>) = CurseFetchCommand().main(args)

private class CurseFetchCommand : SuspendingCliktCommand() {
    init {
        subcommands(ListFiles(), DownloadFile())
    }

    override suspend fun run() {
    }

    class ListFiles : SuspendingCliktCommand() {
        val projectId by argument(help = "The project ID").int()
        val pageSize by option(help = "The page size per batch").int().default(50)
        val limit by option(help = "The limit for the content").int()

        override suspend fun run() {
            val limit = limit
            var pageSize = pageSize
            // don't fetch more than expected
            if (limit != null) pageSize = pageSize.coerceAtMost(limit)
            var flow = client.getModFilesAsFlow(projectId, pageSize)
            // set the limit
            if (limit != null) {
                flow = flow.take(limit)
            }
            // real fetch and print the result
            var counter = 0
            flow.collect { data ->
                counter += data.size
                printTable(data.map { f -> listOf(f.id, f.fileName, f.downloadCount, f.downloadUrl).toStringList() })
            }
            println("Found $counter files in total")
        }
    }

    class DownloadFile : SuspendingCliktCommand() {
        val projectId by argument(help = "The project ID").int()
        val fileId by argument(help = "The file ID").int()

        val output by option(help = "The output path").path()
        val anr by option(help = "Whether enable Automatic Network Replenishment (ANR)").flag()

        override suspend fun run() {
            val result = client.getModFile(projectId, fileId)
            result.onFailure { e ->
                println("Failed to fetch the file information")
                e.printStackTrace()
                return
            }

            val file = result.getOrThrow().data
            println("Downloading ${file.displayName}")
            if (anr) println("Automatic Network Replenishment (Experimental) is enabled")

            val output = output
            val dest =
                if (output != null) {
                    if (output.isDirectory()) output.resolve(file.fileName) else output
                } else {
                    Path("./${file.fileName}")
                }
            // also time the time
            val timeElapsed =
                measureTime {
                    client.getHttpClient().downloadTo(
                        file.downloadUrl,
                        dest,
                        suspend { sent, total ->
                            if (total != null) {
                                val percent = sent * 100.0 / total
                                // xx.xx
                                val percentStr = "%.2f".format(percent).padStart(5, ' ')
                                print("\rDownloaded $percentStr% (${sent.toDiskSpaceSize()} / ${total.toDiskSpaceSize()})")
                            } else {
                                print("\rDownloaded ${sent.toDiskSpaceSize()}")
                            }
                        },
                        anr,
                    )
                }
            println()
            println("Finished, $timeElapsed elapsed")
        }
    }
}

private fun List<*>.toStringList(): List<String> = map { it.toString() }

private fun printTable(rows: List<List<String>>) {
    if (rows.isEmpty()) return

    val maxLength = arrayOfNulls<Int>(rows.first().size)
    for ((index, _) in maxLength.withIndex()) {
        // find the longest value in the column
        maxLength[index] = rows.maxOf { row -> row.getOrNull(index)?.length ?: 0 }
    }

    val s =
        buildString {
            rows.forEach { row ->
                val maxSize = maxLength.size
                maxLength.forEachIndexed { index, len ->
                    append(row[index].padStart(len ?: 0, ' '))
                    if (index + 1 != maxSize) append(' ') // separator
                }
                appendLine()
            }
        }
    println(s)
}

private val diskUnits = listOf("bytes", "kB", "MB", "GB", "TB", "??")

private fun Long.toDiskSpaceSize(): String {
    var i = 0
    var n = this
    while (n > 1024 * 10) {
        n /= 1024
        i++
    }
    return "$n ${diskUnits[i]}"
}
