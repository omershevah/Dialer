package dialer.build

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates an `R` class in each of Soong's `--extra-packages`, so code in, say,
 * `com.android.dialer.app` keeps compiling against `com.android.dialer.app.R`.
 *
 * aapt2 would give every extra package a full copy of the app's R class. With ~100 packages that is
 * close to a million fields, so instead the constants are emitted once, as interfaces in
 * [SHARED_CLASS], and each extra R class nests small classes that implement them. Java resolves
 * `com.android.dialer.app.R.string.foo` to the inherited constant, which is still a compile-time
 * constant usable in `switch`. Kotlin cannot read static members through a subclass, so packages
 * whose R class Kotlin code imports get a full copy.
 */
@CacheableTask
abstract class GenerateExtraRClassesTask : DefaultTask() {

    /** AGP's runtime symbol list output for the variant (contains `R.txt`). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val symbolListDir: DirectoryProperty

    /** The app namespace; AGP already generates its R class. */
    @get:Input
    abstract val namespace: Property<String>

    @get:Input
    abstract val packages: ListProperty<String>

    /** Kotlin sources, scanned for `import <package>.R`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private class Symbol(val javaType: String, val name: String, val value: String)

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val rTxt =
            symbolListDir.get().asFile.walkTopDown().firstOrNull { it.name == "R.txt" }
                ?: error("R.txt not found under ${symbolListDir.get().asFile}")
        val symbols = parse(rTxt)

        // A Kotlin file sees the R class of its own package without an import.
        val rPattern =
            Regex("""^(?:import\s+([\w.]+)\.R(?:\s+as\s+\w+)?|package\s+([\w.]+))\s*$""", RegexOption.MULTILINE)
        val kotlinImported =
            kotlinSources.files
                .filter { it.extension == "kt" }
                .flatMap { file ->
                    rPattern.findAll(file.readText()).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                }
                .toSet()

        val extra = packages.get().distinct().filter { it != namespace.get() }
        if (extra.any { it !in kotlinImported }) {
            writeClass(out, SHARED_PACKAGE, SHARED_CLASS, sharedClass(symbols))
        }
        for (pkg in extra) {
            val body = if (pkg in kotlinImported) fullRClass(pkg, symbols) else thinRClass(pkg, symbols)
            writeClass(out, pkg, "R", body)
        }
    }

    private fun parse(rTxt: File): Map<String, List<Symbol>> =
        rTxt.readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(' ', limit = 4)
                Symbol(javaType = parts[0], name = parts[2], value = parts[3]).let { parts[1] to it }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()

    private fun sharedClass(symbols: Map<String, List<Symbol>>) = buildString {
        appendLine("package $SHARED_PACKAGE;")
        appendLine()
        appendLine("/** Resource IDs shared by the generated extra-package R classes. */")
        appendLine("public final class $SHARED_CLASS {")
        appendLine("  private $SHARED_CLASS() {}")
        for ((type, list) in symbols) {
            appendLine("  public interface $type {")
            for (s in list) appendLine("    ${s.javaType} ${s.name} = ${s.value};")
            appendLine("  }")
        }
        appendLine("}")
    }

    private fun thinRClass(pkg: String, symbols: Map<String, List<Symbol>>) = buildString {
        appendLine("package $pkg;")
        appendLine()
        appendLine("public final class R {")
        appendLine("  private R() {}")
        for (type in symbols.keys) {
            appendLine("  public static final class $type implements $SHARED_PACKAGE.$SHARED_CLASS.$type {")
            appendLine("    private $type() {}")
            appendLine("  }")
        }
        appendLine("}")
    }

    private fun fullRClass(pkg: String, symbols: Map<String, List<Symbol>>) = buildString {
        appendLine("package $pkg;")
        appendLine()
        appendLine("public final class R {")
        appendLine("  private R() {}")
        for ((type, list) in symbols) {
            appendLine("  public static final class $type {")
            appendLine("    private $type() {}")
            for (s in list) appendLine("    public static final ${s.javaType} ${s.name} = ${s.value};")
            appendLine("  }")
        }
        appendLine("}")
    }

    private fun writeClass(out: File, pkg: String, name: String, body: String) {
        File(out, "${pkg.replace('.', '/')}/$name.java").apply {
            parentFile.mkdirs()
            writeText("// Generated by the Gradle build from R.txt. Do not edit.\n$body")
        }
    }

    companion object {
        const val SHARED_PACKAGE = "com.android.dialer.gradlebuild"
        const val SHARED_CLASS = "SharedR"
    }
}
