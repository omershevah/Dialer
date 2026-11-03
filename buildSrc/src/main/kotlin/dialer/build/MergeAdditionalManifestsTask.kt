package dialer.build

import com.android.manifmerger.ManifestMerger2
import com.android.manifmerger.MergingReport
import com.android.utils.StdLogger
import dialer.build.Xml.childElements
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Internal
import org.w3c.dom.Element

/**
 * Merges the app manifest with every per-package `AndroidManifest.xml`, like Soong's
 * `additional_manifests`.
 *
 * AGP accepts a single main manifest per source set, so the result of this task becomes the main
 * manifest. Settings that AGP takes from the DSL (`package`, versions, `<uses-sdk>`) are removed
 * from the result.
 */
@CacheableTask
abstract class MergeAdditionalManifestsTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainManifest: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalManifests: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @get:Internal
    abstract val workDir: DirectoryProperty

    @TaskAction
    fun merge() {
        val work = workDir.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }

        // Sub-manifests use names relative to their own package. Qualify them first so the
        // result does not depend on how the merger resolves names inside libraries. <uses-sdk>
        // comes from the Gradle DSL; the merger rejects it in manifests.
        val libraries =
            additionalManifests.files.sortedBy { it.path }.mapIndexed { i, manifest ->
                val doc = Xml.parse(manifest)
                qualifyComponentNames(doc.documentElement)
                removeUsesSdk(doc.documentElement)
                File(work, "$i/AndroidManifest.xml").also { Xml.write(doc, it) }
            }
        val main =
            Xml.parse(mainManifest.get().asFile).let { doc ->
                removeUsesSdk(doc.documentElement)
                File(work, "main/AndroidManifest.xml").also { Xml.write(doc, it) }
            }

        val invoker =
            ManifestMerger2.newMerger(
                main,
                StdLogger(StdLogger.Level.WARNING),
                ManifestMerger2.MergeType.APPLICATION,
            )
        libraries.forEach { invoker.addLibraryManifest(it) }
        val report = invoker.merge()

        if (report.result.isError) {
            val log = report.loggingRecords.joinToString("\n") { it.toString() }
            throw IllegalStateException("Merging the Dialer sub-manifests failed:\n$log")
        }

        val merged =
            Xml.parse(
                report.getMergedDocument(MergingReport.MergedManifestKind.MERGED)
                    ?: error("Manifest merger produced no document")
            )
        val root = merged.documentElement
        root.removeAttribute("package")
        root.removeAttributeNS(ANDROID_NS, "versionCode")
        root.removeAttributeNS(ANDROID_NS, "versionName")
        removeUsesSdk(root)

        Xml.write(merged, outputManifest.get().asFile)
    }

    private fun removeUsesSdk(manifest: Element) {
        manifest.childElements().filter { it.tagName == "uses-sdk" }.forEach { manifest.removeChild(it) }
    }

    private fun qualifyComponentNames(manifest: Element) {
        val pkg = manifest.getAttribute("package")
        if (pkg.isEmpty()) return

        fun qualify(element: Element, attribute: String) {
            val value = element.getAttributeNS(ANDROID_NS, attribute)
            when {
                value.isEmpty() -> Unit
                value.startsWith(".") -> element.setAttributeNS(ANDROID_NS, "android:$attribute", pkg + value)
                !value.contains('.') -> element.setAttributeNS(ANDROID_NS, "android:$attribute", "$pkg.$value")
            }
        }

        for (application in manifest.childElements().filter { it.tagName == "application" }) {
            qualify(application, "name")
            qualify(application, "backupAgent")
            for (component in application.childElements()) {
                when (component.tagName) {
                    "activity", "service", "receiver", "provider" -> qualify(component, "name")
                    "activity-alias" -> {
                        qualify(component, "name")
                        qualify(component, "targetActivity")
                    }
                }
                if (component.tagName == "activity" || component.tagName == "activity-alias") {
                    qualify(component, "parentActivityName")
                }
            }
        }
    }
}
