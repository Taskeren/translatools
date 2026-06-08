package cn.elytra.translatools.api.project

import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.api.annotation.PathMarker.Relative
import cn.elytra.translatools.api.handler.*
import cn.elytra.translatools.api.utils.ConsoleLogger
import cn.elytra.translatools.api.utils.info
import cn.elytra.translatools.api.utils.warn
import cn.elytra.translatools.internal.RemoteAssociatedIndexedFileStorage
import cn.elytra.translatools.internal.RemoteAssociatedIndexedFileStorage.Status
import cn.elytra.translatools.internal.SharedObjects
import cn.elytra.translatools.internal.Symbols.ARROW_R
import cn.elytra.translatools.internal.Symbols.ARROW_RET
import cn.elytra.translatools.internal.Symbols.DIAMOND_V
import cn.elytra.translatools.internal.Symbols.NO
import cn.elytra.translatools.internal.Symbols.TRI_R
import cn.elytra.translatools.internal.Symbols.TRI_RH
import cn.elytra.translatools.internal.forEachDecomposited
import cn.elytra.translatools.internal.forEachDecompositedSuspend
import cn.elytra.translatools.internal.platform.Paratranz
import cn.elytra.translatools.internal.platform.toTranslationItem
import cn.elytra.translatools.internal.utils.Checksum.md5String
import cn.elytra.translatools.internal.utils.PathSerializer
import cn.elytra.translatools.internal.utils.decodeFromStringFromPath
import cn.elytra.translatools.internal.utils.encodeToStringToPath
import cn.elytra.translatools.internal.utils.forEachCoroutineScope
import io.ktor.client.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.*

public class Project(
    public val config: ProjectConfig,
    public val pathConfig: @PathMarker.Absolute Path,
    public var json: Json = defaultJson,
) {
    /**
     * The directory of the project.
     * a.k.a., the parent directory of the configuration file.
     */
    public val projectDirectory: @PathMarker.Absolute Path

    /**
     * The directory of the sources (e.g., `overrides` of Minecraft modpacks).
     */
    public val sourcesDirectory: @PathMarker.Absolute Path

    /**
     * The indexed files.
     */
    public var index: List<IndexedFile> = emptyList()
        private set

    private val indexPath: @PathMarker.Absolute Path get() = projectDirectory / "project.index.json"

    /**
     * Overrideable environment variable provider
     */
    public var getEnvVar: (name: String) -> String? = { System.getenv(it) }

    /**
     * The paratranz client created by [createParatranzClient].
     */
    internal val paratranz: Paratranz by lazy { createParatranzClient() }

    init {
        // check!
        check(pathConfig.isAbsolute) { "Path to the configuration file must be absolute." }
        check(!config.root.isAbsolute) { "Path to root must be relative to the parent of the configuration file." }
        projectDirectory = checkNotNull(pathConfig.parent) { "ProjectDirectory is null? Something must be wrong." }
        sourcesDirectory = projectDirectory / config.root
        check(sourcesDirectory.isDirectory()) { "Path $sourcesDirectory must be a directory." }

        // try to load the index
        loadIndex()
    }

    public companion object {
        private val log = LoggerFactory.getLogger(Project::class.java)
        private val defaultJson = SharedObjects.encodingJson

        public fun loadFrom(configurationPath: Path): Project {
            val config = Json.decodeFromStringFromPath<ProjectConfig>(configurationPath)
            return Project(config, configurationPath)
        }

        public fun createAt(
            projectDirectory: Path,
            sourcesDirectory: Path,
        ): Project {
            val config = ProjectConfig(root = sourcesDirectory)
            return Project(config, projectDirectory.absolute() / "translatools.json")
        }
    }

    /**
     * Walk the paths in the sources that are matching the glob of the handler.
     */
    private fun FileHandler.asSequence(): Sequence<@PathMarker.Absolute Path> = glob.asSequence().flatMap { resolveGlob(it) }

    private fun loadIndex() {
        if (indexPath.exists()) {
            index = json.decodeFromStringFromPath(indexPath)
        } else {
            log.debug("Index {} doesn't exist", indexPath)
        }
    }

    internal fun saveIndex() {
        json.encodeToStringToPath(index, indexPath)
    }

    internal fun saveConfig() {
        json.encodeToStringToPath(config, pathConfig)
    }

    /**
     * Resolve the relative paths to the sources directory.
     */
    public fun resolve(path: Path): @PathMarker.Absolute Path {
        require(!path.isAbsolute) { "Path $path must be relative." }
        return sourcesDirectory / path
    }

    /**
     * Walk the paths in the sources directory that are matching the glob.
     *
     * @param glob the glob to match the path.
     */
    public fun resolveGlob(
        glob:
            @Relative(relativeTo = "sourcesDirectory")
            String,
    ): Sequence<@PathMarker.Absolute Path> {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$glob")
        return sourcesDirectory.walk().filter { path -> matcher.matches(path.relativeTo(sourcesDirectory)) }
    }

    private fun createParatranzClient(): Paratranz {
        val token =
            getEnvVar("PARATRANZ_TOKEN") ?: throw ProjectException("Missing paratranz token (env: PARATRANZ_TOKEN)")
        return Paratranz(token, HttpClient())
    }

    private fun paratranzProjectId(): Int = config.paratranz?.projectId ?: throw ProjectConfigException("Missing paratranz.projectId")

    context(console: ConsoleLogger)
    internal fun consoleRunIndex(panicDoubleIndexedFile: Boolean = false) {
        val storage = RemoteAssociatedIndexedFileStorage(index, emptyList())
        val pathsMissing = storage.paths.toMutableSet()

        val index = mutableListOf<IndexedFile>()
        val visited =
            hashMapOf<
                @Relative(relativeTo = "sourcesDirectory")
                Path,
                FileHandler,
            >()
        config.handlers.forEach { fileHandler ->
            console.info { "$TRI_R ${fileHandler.uses.name}" }
            fileHandler.glob.forEach { console.info { "$TRI_RH $it" } }
            val handler = fileHandler.uses
            var anyMatched = false
            fileHandler
                .asSequence()
                .associateWith { it.relativeTo(sourcesDirectory) }
                .onEach { (absolute, relative) ->
                    anyMatched = true
                    // update missing paths
                    pathsMissing.remove(relative)
                    // check indexed twice
                    val firstFileHandler = visited[relative]
                    if (firstFileHandler != null) {
                        if (panicDoubleIndexedFile) {
                            throw DoubleIndexedFileException(absolute, fileHandler.uses.name)
                        } else {
                            console.warn { "$NO $relative is indexed twice by $firstFileHandler and $fileHandler, the latter is skipped" }
                        }
                    } else {
                        visited[relative] = fileHandler
                    }
                }.forEach { (absolute, relative) ->
                    val indexHash = md5String(absolute)

                    val oldIndex = storage.getLocal(relative)
                    val newIndex =
                        oldIndex?.copy(lastIndexHash = indexHash)
                            ?: IndexedFile(path = relative, handler = handler, lastIndexHash = indexHash)
                    index.add(newIndex)

                    console.info {
                        when {
                            oldIndex == null -> "[+] $relative added"
                            oldIndex.lastIndexHash == indexHash -> "[=] $relative unmodified"
                            oldIndex.lastIndexHash != indexHash -> "[*] $relative modified"
                            else -> "[?] $relative unreachable state"
                        }
                    }
                }
            if (!anyMatched) {
                console.warn {
                    val globString = fileHandler.glob.joinToString(", ", "[", "]")
                    "$TRI_RH ${fileHandler.uses} $globString doesn't match anything"
                }
            }
        }
        pathsMissing.forEach { path ->
            console.info { "[-] $path removed" }
        }

        this.index = index
        saveIndex()
    }

    context(console: ConsoleLogger)
    internal suspend fun consoleShowDiff(showUnmodified: Boolean = false) {
        val paratranz = paratranz
        val projectId = paratranzProjectId()

        val remote = paratranz.getFileList(projectId)
        val storage = RemoteAssociatedIndexedFileStorage(index, remote)

        val allStatus = storage.allByStatus()

        allStatus[Status.MODIFIED]?.forEachDecomposited { path, local, remote ->
            checkNotNull(local)
            checkNotNull(remote)
            console.info {
                "(*) $path modified (remote ${remote.hash} ≠ local ${local.lastUploadHash}) last updated at ${remote.updatedAt}"
            }
        }

        allStatus[Status.LOCAL_MISSING]?.forEachDecomposited { path, _, remote ->
            checkNotNull(remote)
            console.info { "(-) $path deleted (remote ${remote.hash}) last updated at ${remote.updatedAt}" }
        }

        allStatus[Status.REMOTE_MISSING]?.forEachDecomposited { path, local, _ ->
            checkNotNull(local)
            console.info { "(+) $path added" }
        }

        if (showUnmodified) {
            allStatus[Status.UNMODIFIED]?.forEachDecomposited { path, _, _ ->
                console.info { "(=) $path unmodified" }
            }
        }
    }

    context(console: ConsoleLogger)
    internal suspend fun consoleUploadRemote(
        deleteUnindexedFiles: Boolean = false,
        dryRun: Boolean = false,
        dryRunHandler: Boolean = false,
        checkHash: Boolean,
        forceUpdate: Boolean,
    ) {
        val paratranz = paratranz
        val projectId = paratranzProjectId()

        val storage = RemoteAssociatedIndexedFileStorage(index, paratranz.getFileList(projectId).removeJsonExtension())
        val allStatus = storage.allByStatus()

        /**
         * Run only if not [dryRun] and catches fire!
         */
        suspend fun wetRun(block: suspend () -> Unit) {
            if (!dryRun) {
                try {
                    block()
                } catch (e: Exception) {
                    console.warn(e) { "$NO Unexpected error!" }
                }
            }
        }

        // update existing in both
        val toUpdate = allStatus[Status.MODIFIED] + allStatus[Status.UNMODIFIED].takeIf { forceUpdate }
        console.info { "$TRI_R Updating existing paths (${toUpdate?.size ?: 0})" }
        toUpdate?.forEachDecompositedSuspend { path, local, remote ->
            checkNotNull(local)
            checkNotNull(remote)

            val data =
                try {
                    val translation = local.getTranslationItems()
                    Json.encodeToString(translation)
                } catch (e: Exception) {
                    console.warn(e) { "$NO Failed to generate translation data for path $path" }
                    return@forEachDecompositedSuspend
                }
            if (dryRunHandler) {
                console.info { data }
                return@forEachDecompositedSuspend
            }

            console.info { "$ARROW_R $path" }
            wetRun {
                val localHash = md5String(data)
                val response = paratranz.updateFile(projectId, remote.id, data, path.name.appendJsonExtension())
                // update index
                if (response is Paratranz.FileUpdateResponse.Uploaded) {
                    if (checkHash) checkHash(path, localHash, response.data.file.hash)
                    local.lastUploadHash = response.data.file.hash
                } else {
                    console.info { "$ARROW_RET $path doesn't have content update" }
                }
            }
        }
        // upload new
        val toUpload = allStatus[Status.REMOTE_MISSING]
        console.info { "$TRI_R Uploading new paths (${toUpload?.size ?: 0})" }
        toUpload?.forEachDecompositedSuspend { path, local, _ ->
            checkNotNull(local)

            val data =
                try {
                    val translation = local.getTranslationItems()
                    Json.encodeToString(translation)
                } catch (e: Exception) {
                    console.warn(e) { "$NO Failed to generate translation data for path $path" }
                    return@forEachDecompositedSuspend
                }
            if (dryRunHandler) {
                console.info { data }
                return@forEachDecompositedSuspend
            }

            console.info { "$ARROW_R $path" }
            wetRun {
                val localHash = md5String(data)
                val response = paratranz.uploadFile(projectId, data, path.appendJsonExtension())
                if (checkHash) checkHash(path, localHash, response.file.hash)
                // update index
                local.lastUploadHash = response.file.hash
            }
        }
        // delete removed
        if (deleteUnindexedFiles) {
            val toDelete = allStatus[Status.LOCAL_MISSING]
            console.info { "$TRI_R Deleting removed paths (${toDelete?.size ?: 0})" }
            toDelete?.forEachDecompositedSuspend { path, _, remote ->
                checkNotNull(remote)

                console.info { "$ARROW_R $path" }
                wetRun {
                    paratranz.deleteFile(projectId, remote.id)
                    // nothing to update in index
                }
            }
        }

        wetRun {
            // write the index
            saveIndex()
        }
    }

    context(console: ConsoleLogger)
    internal suspend fun consoleAssembleRemote(
        output: TranslationOutputManager,
        translationPredicate: (Paratranz.TranslationEntry) -> Boolean,
    ) {
        val paratranz = paratranz
        val projectId = paratranzProjectId()

        val storage = RemoteAssociatedIndexedFileStorage(index, paratranz.getFileList(projectId).removeJsonExtension())

        index.forEachCoroutineScope { local ->
            val path = local.path
            val remote =
                storage.getRemote(path)
                    ?: return@forEachCoroutineScope console.warn { "$NO $path is missing in remote, skipped" }

            val translation =
                try {
                    paratranz
                        .getFileTranslation(projectId, remote.id)
                        .filter(translationPredicate)
                        .map { it.toTranslationItem() }
                } catch (e: Exception) {
                    console.warn(e) { "$NO $path translation can't be acquired" }
                    return@forEachCoroutineScope
                }

            context(output) {
                try {
                    val handler = local.handler.get()
                    handler.assembleTranslated(sourcesDirectory, path, translation)
                    console.info { "$DIAMOND_V $path assembled" }
                } catch (e: Exception) {
                    console.warn(e) { "$NO Failed to assemble $path" }
                }
            }
        }
    }
}

/**
 *
 * ### Hashing
 * - To check if the file is updated locally, we use [lastIndexHash], which records the MD5 hash of the sources when indexing.
 * - To check if the file needs to be updated in remote, we use [lastUploadHash], which records the MD5 hash of the generated translation data when uploading. Because the handler must generate their data consistently.
 */
@Serializable
public data class IndexedFile(
    @Serializable(with = PathSerializer::class)
    val path:
        @Relative(relativeTo = "sourcesDirectory")
        Path,
    var handler: HandlerName,
    var lastIndexHash: String,
    var lastUploadHash: String? = null,
)

context(project: Project)
public fun IndexedFile.hashMD5(): String = md5String(project.sourcesDirectory / path)

context(project: Project)
public fun IndexedFile.getTranslationItems(): List<TranslationItem> =
    handler.get().extractUntranslated(project.sourcesDirectory / this.path)

private operator fun <K, V> Map<K, V>?.plus(other: Map<K, V>?): Map<K, V>? {
    if (this == null) return other
    if (other == null) return this
    return LinkedHashMap(this).apply { putAll(other) }
}

private fun Path.appendJsonExtension(): Path = if (this.extension != "json") Path(this.pathString + ".json") else this

private fun String.appendJsonExtension(): String = if (!this.endsWith(".json")) "$this.json" else this

private fun List<Paratranz.File>.removeJsonExtension(): List<Paratranz.File> =
    map {
        val name = it.name.replace(Regex("""^(.*\..+)\.json$""", RegexOption.IGNORE_CASE), "$1")
        if (name != it.name) it.copy(name = name) else it
    }

private fun checkHash(
    path: Path,
    localHash: String,
    remoteHash: String,
) {
    if (localHash != remoteHash) throw UploadHashMismatchException(path, localHash, remoteHash)
}
