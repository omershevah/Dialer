package dialer.build

import dialer.build.Xml.childElements
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/**
 * Collapses Soong's ordered `resource_dirs` into one resource directory.
 *
 * Soong links every resource directory of an app with static libraries as an aapt2 overlay, in
 * order, so a resource defined in a later directory replaces the same resource from an earlier
 * one. Many Dialer resources (mostly translated strings) are defined in more than one directory.
 * AGP rejects duplicates inside one source set, so this task applies the overlay order up front
 * and keeps only the winning definition of each resource.
 */
@CacheableTask
abstract class MergeOverlayResourcesTask : DefaultTask() {

    /** Resource directories, lowest priority first. Order matters. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resourceDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class ValueKey(val qualifier: String, val type: String, val name: String)

    private data class FileKey(val qualifier: String, val name: String)

    @TaskAction
    fun merge() {
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val dirs = resourceDirs.files.toList() // insertion order of the collection

        // Pass 1: find the highest-priority definition of every resource.
        val valueWinner = HashMap<ValueKey, Int>()
        val fileWinner = LinkedHashMap<FileKey, File>()
        dirs.forEachIndexed { index, dir ->
            for (qualifierDir in subdirs(dir)) {
                val qualifier = qualifierDir.name
                for (file in qualifierDir.listFiles().orEmpty().filter { it.isFile }.sorted()) {
                    if (isValues(qualifier)) {
                        if (!file.name.endsWith(".xml")) continue
                        for (element in Xml.parse(file).documentElement.childElements()) {
                            keyOf(qualifier, element)?.let { valueWinner[it] = index }
                            for (attr in nestedAttrDefinitions(element)) {
                                valueWinner[ValueKey(qualifier, "attr", attr.getAttribute("name"))] = index
                            }
                        }
                    } else {
                        fileWinner[FileKey(qualifier, file.name.substringBefore('.'))] = file
                    }
                }
            }
        }

        // Pass 2: write files, dropping definitions that a later directory overrides.
        for (file in fileWinner.values) {
            file.copyTo(File(out, "${file.parentFile.name}/${file.name}"), overwrite = true)
        }
        dirs.forEachIndexed { index, dir ->
            for (qualifierDir in subdirs(dir).filter { isValues(it.name) }) {
                val qualifier = qualifierDir.name
                for (file in qualifierDir.listFiles().orEmpty().filter { it.name.endsWith(".xml") }) {
                    val target = File(out, "$qualifier/${index}_${file.name}")
                    val doc = Xml.parse(file)
                    val root = doc.documentElement
                    val overridden =
                        root.childElements().filter { element ->
                            val key = keyOf(qualifier, element)
                            key != null && valueWinner[key] != index
                        }
                    // An attr defined inside a styleable and again elsewhere: keep the winning
                    // definition and turn the others into plain references.
                    var demoted = false
                    for (element in root.childElements() - overridden.toSet()) {
                        for (attr in nestedAttrDefinitions(element)) {
                            val key = ValueKey(qualifier, "attr", attr.getAttribute("name"))
                            if (valueWinner[key] != index) {
                                attr.removeAttribute("format")
                                while (attr.hasChildNodes()) attr.removeChild(attr.firstChild)
                                demoted = true
                            }
                        }
                    }
                    when {
                        overridden.isEmpty() && !demoted -> {
                            target.parentFile.mkdirs()
                            file.copyTo(target, overwrite = true)
                        }
                        root.childElements().size > overridden.size -> {
                            overridden.forEach { root.removeChild(it) }
                            Xml.write(doc, target)
                        }
                        else -> Unit // everything here is overridden
                    }
                }
            }
        }
    }

    /** `<attr>` children of a `<declare-styleable>` that define the attr rather than reference it. */
    private fun nestedAttrDefinitions(element: Element): List<Element> =
        if (element.tagName != "declare-styleable") {
            emptyList()
        } else {
            element.childElements().filter { attr ->
                attr.tagName == "attr" &&
                    !attr.getAttribute("name").startsWith("android:") &&
                    (attr.hasAttribute("format") || attr.childElements().isNotEmpty())
            }
        }

    private fun subdirs(dir: File): List<File> =
        dir.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }

    private fun isValues(qualifier: String) = qualifier == "values" || qualifier.startsWith("values-")

    /** The resource identity aapt2 uses for a top-level element of a values file. */
    private fun keyOf(qualifier: String, element: Element): ValueKey? {
        val name = element.getAttribute("name")
        if (name.isEmpty()) return null
        val type =
            when (val tag = element.tagName) {
                "item" -> element.getAttribute("type").ifEmpty { return null }
                "string-array", "integer-array", "array" -> "array"
                "declare-styleable" -> "styleable"
                "public", "java-symbol", "eat-comment", "skip" -> return null
                else -> tag
            }
        return ValueKey(qualifier, type, name)
    }
}
