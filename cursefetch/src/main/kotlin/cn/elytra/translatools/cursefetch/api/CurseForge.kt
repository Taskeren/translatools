package cn.elytra.translatools.cursefetch.api

import cn.elytra.translatools.cursefetch.internal.IntValuedSerializer
import cn.elytra.translatools.cursefetch.internal.parseInt
import kotlinx.serialization.Serializable

object CurseForge {
    @Serializable
    data class File(
        val id: Int,
        val gameId: Int,
        val modId: Int,
        val isAvailable: Boolean,
        val displayName: String,
        val fileName: String,
        val releaseType: FileReleaseType,
        val fileStatus: FileStatus,
        val hashes: List<FileHash>,
        val fileDate: String,
        val fileLength: Long,
        val downloadCount: Long,
        val fileSizeOnDisk: Long? = null,
        val downloadUrl: String,
        val gameVersions: List<String>,
        val sortableGameVersions: List<SortableGameVersion>,
        val dependencies: List<FileDependency>,
        val exposeAsAlternative: Boolean? = null,
        val parentProjectFileId: Int? = null,
        val alternateFileId: Int? = null,
        val isServerPack: Boolean? = null,
        val serverPackFileId: Int? = null,
        val isEarlyAccessContent: Boolean? = null,
        val earlyAccessEndDate: String? = null,
        val fileFingerprint: Long,
        val modules: List<FileModule>,
    )

    @Serializable(with = FileReleaseType.Companion::class)
    enum class FileReleaseType(
        val value: Int,
    ) {
        Release(1),
        Beta(2),
        Alpha(3),
        ;

        companion object :
            IntValuedSerializer<FileReleaseType>(FileReleaseType::value, FileReleaseType::value::parseInt)
    }

    @Serializable(with = FileStatus.Companion::class)
    enum class FileStatus(
        val value: Int,
    ) {
        Processing(1),
        ChangesRequired(2),
        UnderReview(3),
        Approved(4),
        Rejected(5),
        MalwareDetected(6),
        Deleted(7),
        Archived(8),
        Testing(9),
        Released(10),
        ReadyForReview(11),
        Deprecated(12),
        Baking(13),
        AwaitingPublishing(14),
        FailedPublishing(15),
        Cooking(16),
        Cooked(17),
        UnderManualReview(18),
        ScanningForMalware(19),
        ProcessingFile(20),
        PendingRelease(21),
        ReadyForCooking(22),
        PostProcessing(23),
        ;

        companion object : IntValuedSerializer<FileStatus>(FileStatus::value, FileStatus::value::parseInt)
    }

    @Serializable
    data class FileHash(
        val value: String,
        val algo: HashAlgo,
    )

    @Serializable(with = HashAlgo.Companion::class)
    enum class HashAlgo(
        val value: Int,
    ) {
        Sha1(1),
        Md5(2),
        ;

        companion object : IntValuedSerializer<HashAlgo>(HashAlgo::value, HashAlgo::value::parseInt)
    }

    @Serializable
    data class SortableGameVersion(
        val gameVersionName: String,
        val gameVersionPadded: String,
        val gameVersion: String,
        val gameVersionReleaseDate: String,
        val gameVersionTypeId: Int?,
    )

    @Serializable
    data class FileDependency(
        val modId: Int,
        val relationType: FileRelationType,
    )

    @Serializable(with = FileRelationType.Companion::class)
    enum class FileRelationType(
        val value: Int,
    ) {
        EmbeddedLibrary(1),
        OptionalDependency(2),
        RequiredDependency(3),
        Tool(4),
        Incompatible(5),
        Include(6),
        ;

        companion object :
            IntValuedSerializer<FileRelationType>(FileRelationType::value, FileRelationType::value::parseInt)
    }

    @Serializable
    data class FileModule(
        val name: String,
        val fingerprint: Long,
    )

    @Serializable
    data class Pagination(
        val index: Int,
        val pageSize: Int,
        val resultCount: Int,
        val totalCount: Long,
    )
}

@Serializable
data class GetModFilesResponse(
    val data: List<CurseForge.File>,
    val pagination: CurseForge.Pagination,
)

@Serializable
data class GetModFileResponse(
    val data: CurseForge.File,
)
