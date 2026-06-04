package cn.elytra.translatools.internal.platform

import cn.elytra.translatools.api.annotation.PathMarker
import cn.elytra.translatools.api.handler.TranslationItem
import cn.elytra.translatools.internal.SharedObjects
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.readBytes
import kotlin.time.Instant

private const val API_BASE = "https://paratranz.cn/api"

internal class Paratranz(
    var token: String,
    httpClient: HttpClient,
    json: Json = Json,
) {
    private val httpClient =
        httpClient.config {
            defaultRequest {
                header("Authorization", "Bearer $token")
            }

            install(ContentNegotiation) {
                json(SharedObjects.decodingJson)
            }
        }

    /**
     * Get information of files.
     */
    suspend fun getFileList(projectId: Int): List<File> =
        httpClient
            .get("$API_BASE/projects/$projectId/files") {
                // expect success here, error handling later
                expectSuccess = true
            }.body()

    /**
     * Upload a file that doesn't exist in Paratranz.
     * NOTE: it will fail if there's already a file in the path.
     */
    suspend fun uploadFile(
        projectId: Int,
        file: Path,
        filename: String? = null,
    ): FileUploadResponse =
        httpClient
            .submitFormWithBinaryData(
                "$API_BASE/projects/$projectId/files",
                formData {
                    val filename = filename ?: file.name
                    val filenameHeader = headers { append(HttpHeaders.ContentDisposition, "filename=$filename") }
                    append("file", file.readBytes(), filenameHeader)
                    append("path", file.parentOrEmpty.pathString)
                },
            ) {
                expectSuccess = true
            }.body()

    /**
     * Upload a file that doesn't exist in Paratranz.
     * NOTE: it will fail if there's already a file in the path.
     */
    suspend fun uploadFile(
        projectId: Int,
        file: String,
        filePath: @PathMarker.Relative Path,
    ): FileUploadResponse =
        httpClient
            .submitFormWithBinaryData(
                "$API_BASE/projects/$projectId/files",
                formData {
                    val filename = filePath.name
                    val filenameHeader = headers { append(HttpHeaders.ContentDisposition, "filename=$filename") }
                    append("file", file, filenameHeader)
                    append("path", filePath.parentOrEmpty.pathString)
                },
            ) {
                expectSuccess = true
            }.body()

    /**
     * @see getFileList
     */
    suspend fun getFileMetadata(
        projectId: Int,
        fileId: Int,
    ): File =
        httpClient
            .get("$API_BASE/projects/$projectId/files/$fileId") {
                expectSuccess = true
            }.body()

    /**
     * Update the untranslated content of the file.
     * @param filename the filename expected to be the same as the first-time uploaded.
     */
    suspend fun updateFile(
        projectId: Int,
        fileId: Int,
        file: Path,
        filename: String? = null,
    ): FileUploadResponse =
        // The return type is documented as 'File', but it's not the truth.
        httpClient
            .submitFormWithBinaryData(
                "$API_BASE/projects/$projectId/files/$fileId",
                formData {
                    val filename = filename ?: file.name
                    val filenameHeader = headers { append(HttpHeaders.ContentDisposition, "filename=$filename") }
                    append("file", file.readBytes(), filenameHeader)
                },
            ) {
                expectSuccess = true
            }.body()

    /**
     * Update the untranslated content of the file.
     * @param filename the filename expected to be the same as the first-time uploaded.
     */
    suspend fun updateFile(
        projectId: Int,
        fileId: Int,
        file: String,
        filename: String,
    ): FileUpdateResponse =
        // The return type is documented as 'File', but it's not the truth.
        httpClient
            .submitFormWithBinaryData(
                "$API_BASE/projects/$projectId/files/$fileId",
                formData {
                    val filenameHeader = headers { append(HttpHeaders.ContentDisposition, "filename=$filename") }
                    append("file", file, filenameHeader)
                },
            ) {
                expectSuccess = true
            }.body()

    /**
     * Delete the file.
     */
    suspend fun deleteFile(
        projectId: Int,
        fileId: Int,
    ) = httpClient
        .delete("$API_BASE/projects/$projectId/files/$fileId") {
            expectSuccess = true
        }

    @JvmName("+getFileTranslation")
    suspend inline fun <reified T> getFileTranslationAs(
        projectId: Int,
        fileId: Int,
    ): T =
        httpClient
            .get("$API_BASE/projects/$projectId/files/$fileId/translation") {
                expectSuccess = true
            }.body()

    /**
     * Get the translation data of the file.
     *
     * The translation content is structured as this for plain JSON files.
     */
    suspend fun getFileTranslation(
        projectId: Int,
        fileId: Int,
    ): List<TranslationEntry> = getFileTranslationAs<List<TranslationEntry>>(projectId, fileId)

    @TestOnly
    suspend fun getFileTranslationAsString(
        projectId: Int,
        fileId: Int,
    ) = getFileTranslationAs<String>(projectId, fileId)

    suspend fun updateFileTranslation(
        projectId: Int,
        fileId: Int,
        file: Path,
        filename: String? = null,
        force: Boolean? = null,
    ): File =
        httpClient
            .submitFormWithBinaryData(
                "$API_BASE/projects/$projectId/files/$fileId/translation",
                formData {
                    val filename = filename ?: filename
                    val filenameHeader = headers { append(HttpHeaders.ContentDisposition, "filename=$filename") }
                    append("file", file.readBytes(), filenameHeader)
                    if (force != null) {
                        append("force", force)
                    }
                },
            ).body()

    /**
     * @property extra used in uploading, but not sure the content (null by default).
     */
    @Serializable
    data class File(
        val id: Int,
        val createdAt: Instant,
        val updatedAt: Instant,
        val modifiedAt: Instant,
        val name: String,
        val project: Int,
        val format: String,
        val total: Int,
        val translated: Int,
        val disputed: Int,
        val checked: Int,
        val reviewed: Int,
        val hidden: Int,
        val locked: Int,
        val words: Int,
        val hash: String,
        val folder: String,
        val progress: FileProgress,
        val extra: JsonElement? = null,
    )

    @Serializable
    data class FileProgress(
        val translate: Double,
        val review: Double,
        val check: Double,
    )

    /**
     * @property id not sure, probably the upload entry ID
     * @property file the file ID
     * @property hash the hashing in MD5
     */
    @Serializable
    data class Revision(
        val id: Int,
        val file: Int,
        val name: String,
        val filename: String,
        val uid: Int,
        val project: Int,
        val type: RevisionType,
        val hash: String,
        val createdAt: Instant,
        val insert: Int,
        val update: Int,
        val remove: Int,
        val force: Boolean,
        val incremental: Boolean,
        val revertedAt: JsonElement? = null,
    )

    @Serializable
    data class FileUploadResponse(
        val file: File,
        val revision: Revision,
    )

    @JvmInline
    @Serializable
    value class RevisionType(
        val value: String,
    ) {
        companion object {
            /**
             * Create the file
             */
            val CREATE = RevisionType("create")

            /**
             * Update untranslated content
             */
            val UPDATE = RevisionType("update")

            /**
             * Import translations
             */
            val IMPORT = RevisionType("import")
        }
    }

    @JvmInline
    @Serializable
    value class TranslationStage(
        val value: Int,
    ) {
        @Suppress("ktlint:standard:no-multi-spaces")
        companion object {
            val namedValues: Map<String, TranslationStage>
                field = mutableMapOf<String, TranslationStage>()
            internal val valueNames: Map<TranslationStage, String>
                field = mutableMapOf<TranslationStage, String>()

            // @formatter:off
            val UNTRANSLATED = named("UNTRANSLATED", 0)
            val TRANSLATED   = named("TRANSLATED",   1)
            val QUESTION     = named("QUESTION",     2)
            val CHECKED      = named("CHECKED",      3)
            val APPROVED     = named("APPROVED",     5)
            val LOCKED       = named("LOCKED",       9)
            val HIDDEN       = named("HIDDEN",      -1)
            // @formatter:on

            /**
             * Valid values for [TranslationStage].
             */
            val values = listOf(UNTRANSLATED, TRANSLATED, QUESTION, CHECKED, APPROVED, LOCKED, HIDDEN)

            val normalStages = listOf(UNTRANSLATED, TRANSLATED, CHECKED, APPROVED)

            private fun named(
                name: String,
                value: Int,
            ): TranslationStage =
                TranslationStage(value).also {
                    namedValues[name] = it
                    valueNames[it] = name
                }

            fun valueOf(name: String): TranslationStage? = namedValues[name.uppercase(Locale.US)]

            /**
             * Create a predicate for the translation data.
             */
            fun makePredicate(
                leastStage: TranslationStage = TRANSLATED,
                exportQuestion: Boolean = false,
                exportLocked: Boolean = false,
                exportHidden: Boolean = false,
            ): (TranslationEntry) -> Boolean =
                {
                    when (it.stage) {
                        QUESTION -> exportQuestion
                        LOCKED -> exportLocked
                        HIDDEN -> exportHidden
                        else -> it.stage.value >= leastStage.value
                    }
                }

            val TranslationStage.name: String? get() = valueNames[this]
        }

        override fun toString(): String = this.name ?: this.value.toString()
    }

    /**
     * NOTE: this structure is not defined in the document.
     */
    @Serializable
    data class TranslationEntry(
        val id: Int,
        val key: String,
        val original: String,
        val translation: String,
        val stage: TranslationStage,
        val context: String,
    )

    @Serializable(with = FileUpdateResponse.Serializer::class)
    sealed interface FileUpdateResponse {
        /**
         * Indicates that the uploaded content is equal to the existing.
         */
        object HashMatched : FileUpdateResponse

        data class Uploaded(
            val data: FileUploadResponse,
        ) : FileUpdateResponse

        object Serializer : KSerializer<FileUpdateResponse> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FileUpdateResponse")

            override fun serialize(
                encoder: Encoder,
                value: FileUpdateResponse,
            ) {
                error("Serialization not supported")
            }

            override fun deserialize(decoder: Decoder): FileUpdateResponse {
                val decoder = decoder as JsonDecoder
                val root = decoder.decodeJsonElement().jsonObject

                if (root.containsKey("status") && root.getValue("status").jsonPrimitive.content == "hashMatched") {
                    return HashMatched
                } else {
                    println(root)
                    return Uploaded(Json.decodeFromJsonElement<FileUploadResponse>(root))
                }
            }
        }
    }
}

/**
 * Get the parent if capable, or empty path otherwise.
 */
private val Path.parentOrEmpty get() = runCatching { parent.takeIf { it != null } }.getOrNull() ?: Path("")

internal fun Paratranz.TranslationEntry.toTranslationItem() =
    TranslationItem(key = key, original = original, translation = translation, context = context)
