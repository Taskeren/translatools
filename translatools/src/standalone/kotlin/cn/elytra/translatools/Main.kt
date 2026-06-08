package cn.elytra.translatools

import cn.elytra.translatools.api.handler.Handler
import cn.elytra.translatools.api.handler.TranslationOutputManager
import cn.elytra.translatools.api.project.Project
import cn.elytra.translatools.api.utils.ConsoleLogger
import cn.elytra.translatools.cursefetch.CurseFetchCommand
import cn.elytra.translatools.internal.platform.Paratranz.TranslationStage
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import io.github.cdimascio.dotenv.Dotenv
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.system.exitProcess

private var globalDotenv: Dotenv = Dotenv.configure().ignoreIfMissing().load()

suspend fun main(args: Array<String>) =
    TranslatoolsCommand()
        .context {
            // override the source of env-vars
            readEnvvar = { globalDotenv.get(it) }
        }.subcommands(CurseFetchCommand())
        .main(args)

private class TranslatoolsCommand : SuspendingCliktCommand() {
    init {
        subcommands(
            InitCommand(),
            HandlerCommand(),
            IndexCommand(),
            DiffCommand(),
            UploadCommand(),
            AssembleCommand(),
        )
    }

    data class CommandContext(
        val workingDir: Path,
    )

    val workingDir by option("-w", "--working-dir", help = "The directory of the project")
        .path()
        .defaultLazy { Path("") }

    override suspend fun run() {
        currentContext.obj = CommandContext(workingDir)
        // load the dotenv if the working dir is changed
        if (workingDir != Path("")) {
            globalDotenv =
                Dotenv
                    .configure()
                    .directory(workingDir.absolute().pathString)
                    .ignoreIfMissing()
                    .load()
        }
    }

    class InitCommand : SuspendingCliktCommand() {
        val projectPath by argument(help = "The directory of the project that contains the source directory.").path()
        val sourcesPath by argument(help = "The directory of the sources under the project path (default: overrides)")
            .path()
            .defaultLazy { Path("overrides") }

        override suspend fun run() {
            if (projectPath.notExists()) println("Path $projectPath doesn't exist.")
            if (sourcesPath.isAbsolute) println("Source path $sourcesPath must be relative, and relative to the project path.")

            val project = Project.createAt(projectPath, sourcesPath)
            println("Configuration created at ${project.pathConfig}")
            project.saveConfig()
        }
    }

    class HandlerCommand : SuspendingCliktCommand() {
        init {
            subcommands(ListCommand(), ManCommand())
        }

        override suspend fun run() {
        }

        class ListCommand : SuspendingCliktCommand() {
            override suspend fun run() {
                Handler.listAll().forEach { (name, handler) ->
                    val desc = handler.getDescription() ?: handler::class.qualifiedName
                    println("- ${name.padEnd(8, ' ')} $desc")
                }
            }
        }

        class ManCommand : SuspendingCliktCommand() {
            val handler by argument(help = "The handler name to the manual pages")

            override suspend fun run() {
                val hand = Handler.getByName(handler)
                if (hand == null) {
                    println("Handler with name $handler doesn't exist")
                } else {
                    println("=========[ $handler ]=========")
                    println("Desc: ${hand.getDescription() ?: "This handler doesn't provide descriptions"}")
                    println("Class: ${hand::class.qualifiedName}")
                    println(hand.getManualPages() ?: "This handler doesn't provide manual pages")
                }
            }
        }
    }

    class IndexCommand : SuspendingCliktCommand() {
        val context by requireObject<CommandContext>()

        override fun help(context: Context): String = "Indexing the project"

        override suspend fun run() {
            val p = Project.current(context.workingDir)
            context(ConsoleLogger.CONSOLE) {
                p.consoleRunIndex(panicDoubleIndexedFile = false)
            }
        }
    }

    class DiffCommand : SuspendingCliktCommand() {
        val context by requireObject<CommandContext>()

        val showUnmodified by option(help = "Whether to list the unmodified paths").flag()

        override fun help(context: Context): String = "Show the difference between local and remote"

        override suspend fun run() {
            val p = Project.current(context.workingDir)

            context(ConsoleLogger.CONSOLE) {
                p.consoleShowDiff(showUnmodified)
            }
        }
    }

    class UploadCommand : SuspendingCliktCommand() {
        val context by requireObject<CommandContext>()

        val deleteUnindexedFiles by option(
            "-d",
            "--delete-unindexed-files",
            help = "Whether to delete the unindexed (locally removed) files",
        ).flag()
        val forceUpdate by option("-f", "--force-update", help = "Whether to update the unmodified paths").flag()
        val dryRun by option(help = "Simulate the operation").flag()
        val checkHash by option(help = "Whether panic on hash mismatch on uploading/updating").flag()

        // write the result of handlers to the stdout. better write them to a file, because the buffer of consoles usually can't handle the size.
        val dryRunHandler by option(hidden = true).flag()

        override fun help(context: Context): String = "Extract the translation entries and upload/update to remote"

        override suspend fun run() {
            val p = Project.current(context.workingDir)

            context(ConsoleLogger.CONSOLE) {
                p.consoleUploadRemote(deleteUnindexedFiles, dryRun, dryRunHandler, checkHash, forceUpdate)
            }
        }
    }

    class AssembleCommand : SuspendingCliktCommand() {
        val context by requireObject<CommandContext>()

        val exportStage by option("-s", "--stage")
        val exportQuestion by option("-q", "--question", help = "Whether export question entries.").flag()
        val exportLocked by option("-l", "--locked", help = "Whether export locked entries.").flag()
        val exportHidden by option("-h", "--hidden", help = "Whether export hidden entries.").flag()

        val deleteExisting by option("-d", "--delete-existing", help = "Whether delete existing directories.").flag()

        override fun help(context: Context): String = "Download and assemble the translation entries"

        override suspend fun run() {
            val p = Project.current(context.workingDir)

            val translationDirectory = Path("./translation")
            if (deleteExisting) {
                @OptIn(ExperimentalPathApi::class)
                translationDirectory.deleteRecursively()
            }
            val output = TranslationOutputManager(translationDirectory)
            val leastStage =
                exportStage?.let { TranslationStage.valueOf(it) } ?: TranslationStage.TRANSLATED
            if (leastStage !in TranslationStage.normalStages) {
                val normalStagesStr = TranslationStage.normalStages.joinToString(", ", "[", "]")
                println("Least stage $leastStage is not a normal stage. Prefer using $normalStagesStr.")
            }
            val translationPredicate =
                TranslationStage.makePredicate(leastStage, exportQuestion, exportLocked, exportHidden)
            context(ConsoleLogger.CONSOLE) {
                p.consoleAssembleRemote(output, translationPredicate)
            }
            output.executeDelayed()
            println("Dumped to ${output.translationDirectory}")
        }
    }
}

private fun Project.Companion.current(workingDir: Path): Project {
    val path = workingDir / "translatools.json"
    if (path.notExists()) {
        println("There's no translatools configuration in this directory.")
        exitProcess(0)
    }
    return Project.loadFrom(path.absolute()).also { it.getEnvVar = globalDotenv::get }
}
