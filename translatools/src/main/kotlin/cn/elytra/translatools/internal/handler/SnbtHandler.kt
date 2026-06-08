package cn.elytra.translatools.internal.handler

import cn.elytra.ftbsnbt.SNBT
import cn.elytra.translatools.api.handler.Handler
import cn.elytra.translatools.api.handler.TranslationItem
import cn.elytra.translatools.api.handler.TranslationOutputManager
import cn.elytra.translatools.internal.utils.expected
import org.glavo.nbt.NBTPath
import org.glavo.nbt.internal.path.NBTPathImpl
import org.glavo.nbt.internal.path.NBTPathNode
import org.glavo.nbt.io.SNBTCodec
import org.glavo.nbt.tag.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readLines

internal object SnbtHandler : Handler<CompoundTag> {
    private val panicNonStringTag = System.getProperty("handler.snbt.panicNonStringTag").toBoolean()

    override fun loadData(path: Path): CompoundTag = SNBT.readLines(path.readLines())

    override fun extractData(data: CompoundTag): List<TranslationItem> =
        buildList {
            data.walkPath().forEach { (path, tag) ->
                if (tag !is StringTag) {
                    if (panicNonStringTag) error("Unexpected tag type: ${tag.type}, only String allowed.") else return@forEach
                }
                val path = path.toPathString()
                this.add(TranslationItem(path, tag.asString))
            }
        }

    override fun injectData(
        data: CompoundTag,
        translate: List<TranslationItem>,
    ) {
        val translatedTags = mutableSetOf<Tag>()
        translate.forEach { (key, _, translation) ->
            val path =
                runCatching { NBTPath.of(key).withTagType(TagType.STRING) }
                    .expected { IllegalStateException("Failed to parse NBT path: '$key'", it) }
            val allTags = data.getAllTags(path).toList()
            val tag =
                when (allTags.size) {
                    0 -> error("Path $path selected nothing\n$data")
                    1 -> allTags.single()
                    else -> error("Path $path selected multiple tags: ${allTags.joinToString()}")
                }
            tag.set(translation)
            translatedTags.add(tag)
        }
        // remove untranslated entries
        // walk() is breath-first
        data
            .walk()
            .toList()
            .filterNot { it in translatedTags }
            .forEach { it.parentTag?.removeTag(it) }
        // remove empty parent tags
        data
            .walkAny()
            .filterIsInstance<ParentTag<*>>()
            .filter { it.isEmpty }
            .forEach { it.parentTag?.removeTag(it) }
    }

    context(output: TranslationOutputManager)
    override fun saveData(
        data: CompoundTag,
        sourcesPath: Path,
    ) {
        if (data.isEmpty) return // don't export empty data
        val path = with(output) { sourcesPath.replaceLocale() }
        output.addContent(path, SNBTCodec.of().toString(data))
    }
}

private fun Tag.walk(): Sequence<ValueTag<*>> = walkAny().filterIsInstance<ValueTag<*>>()

private fun Tag.walkAny(): Sequence<Tag> =
    sequence {
        val queue = ArrayDeque<Tag>().also { it.add(this@walkAny) }
        while (queue.isNotEmpty()) {
            val tag = queue.poll()
            yield(tag)
            if (tag is ParentTag<*>) tag.forEach { queue.offer(it) }
        }
    }

/**
 * Walk the whole tag tree, depth-first.
 */
private fun ParentTag<*>.walkPath(): Sequence<Pair<NBTPath<ValueTag<*>>, ValueTag<*>>> =
    sequence {
        // stack of path nodes and the tag
        val stack =
            ArrayDeque<Pair<List<NBTPathNode>, Tag>>().also { it.add(emptyList<NBTPathNode>() to this@walkPath) }
        while (stack.isNotEmpty()) {
            val (paths, tag) = stack.pop()
            when (tag) {
                is ParentTag<*> -> {
                    tag.forEach {
                        stack.push(paths + NBTPathImpl.getIndicator(it)!! to it)
                    }
                }

                is ValueTag<*> -> {
                    yield(NBTPathImpl(paths.toTypedArray(), tag.type as TagType<ValueTag<*>>) to tag)
                }
            }
        }
    }
