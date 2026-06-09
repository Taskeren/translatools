package cn.elytra.translatools.internal.handler

import cn.elytra.ftbsnbt.SNBT
import cn.elytra.translatools.api.TranslationOutputManager
import cn.elytra.translatools.api.handler.*
import cn.elytra.translatools.api.handler.Handler.Companion.lazyBuildList
import cn.elytra.translatools.api.utils.ProjectPath
import cn.elytra.translatools.api.utils.readLines
import org.glavo.nbt.tag.CompoundTag
import org.glavo.nbt.tag.ListTag
import org.glavo.nbt.tag.StringTag

internal object FTBQuestsForciblyTranslatedHandler : Handler {
    private const val CHAPTER_GROUP_TEMPLATE = "ftbquests.chapter_groups_%s.title"
    private const val CHAPTER_TITLE_TEMPLATE = "ftbquests.chapter.%s.title"
    private const val QUEST_TITLE_TEMPLATE = "ftbquests.chapter.%s.quest%s.title"
    private const val QUEST_SUBTITLE_TEMPLATE = "ftbquests.chapter.%s.quest%s.subtitle"
    private const val QUEST_DESCRIPTION_TEMPLATE = "ftbquests.chapter.%s.quest%s.description%s"

    override fun extractEntries(context: ExtractContext): Map<ProjectPath, () -> List<TranslationItem>> {
        val globs = context.globs
        val glob =
            when (globs.size) {
                0 -> "config/ftbquests/quests"
                1 -> globs.single()
                else -> error("Unexpected number of globs (${globs.size}), expected 0-1.")
            }
        val directory = context.sourcesDirectory / glob

        val result = mutableMapOf<ProjectPath, () -> List<TranslationItem>>()

        val pathChapterGroups = directory / "chapter_groups.snbt"
        result[pathChapterGroups] =
            lazyBuildList {
                val chapterGroupsData = SNBT.readLines((directory / "chapter_groups.snbt").readLines())
                @Suppress("UNCHECKED_CAST")
                for (chapterGroupData in chapterGroupsData.get("chapter_groups") as ListTag<CompoundTag>) {
                    this +=
                        TranslationItem(
                            CHAPTER_GROUP_TEMPLATE.format(chapterGroupData.getString("id")),
                            chapterGroupData.getString("title"),
                        )
                }
            }

        val pathChapters = (directory / "chapters").walkGlob("*.snbt")
        pathChapters.forEach { pathChapter ->
            result[pathChapter] =
                lazyBuildList {
                    val chapterData = SNBT.readLines(pathChapter.readLines())
                    val chapterFilename = chapterData.getString("filename")

                    @Suppress("UNCHECKED_CAST")
                    val quests = chapterData.get("quests") as ListTag<CompoundTag>

                    chapterData.getStringOrNull("title")?.let {
                        this += TranslationItem(CHAPTER_TITLE_TEMPLATE.format(chapterFilename), it)
                    }

                    quests.forEachIndexed { questIndex, questData ->
                        val title = questData.getStringOrNull("title")
                        val subtitle = questData.getStringOrNull("subtitle")

                        @Suppress("UNCHECKED_CAST")
                        val descriptions = questData.get("description") as ListTag<StringTag>?

                        val contextString =
                            buildString {
                                appendLine("[TITLE] ${title.orEmpty()}")
                                appendLine("[SUBTITLE] ${subtitle.orEmpty()}")
                                appendLine("[DESCRIPTIONS (${descriptions?.size() ?: 0} lines)]")
                                descriptions?.forEach { tag -> appendLine(tag.asString) }
                            }

                        // title
                        title?.let {
                            this +=
                                TranslationItem(
                                    key = QUEST_TITLE_TEMPLATE.format(chapterFilename, questIndex + 1),
                                    original = it,
                                    context = contextString,
                                )
                        }
                        // subtitle
                        subtitle?.let {
                            this +=
                                TranslationItem(
                                    key = QUEST_SUBTITLE_TEMPLATE.format(chapterFilename, questIndex + 1),
                                    original = it,
                                    context = contextString,
                                )
                        }
                        // descriptions
                        descriptions?.let {
                            it.forEachIndexed { descIndex, descTag ->
                                val key =
                                    QUEST_DESCRIPTION_TEMPLATE.format(chapterFilename, questIndex + 1, descIndex + 1)
                                this += TranslationItem(key = key, original = descTag.asString, context = contextString)
                            }
                        }
                    }
                }
        }

        return result
    }

    override fun insertEntries(
        context: InsertContext,
        output: TranslationOutputManager,
    ) {
        context.entries.forEach { (_, items) ->
            items.forEach { (key, _, translation) ->
                if (translation != null) output.addLanguage(key, translation)
            }
        }
    }
}
