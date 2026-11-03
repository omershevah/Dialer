package dialer.build

import java.io.File

/**
 * The parts of the Soong `Android.bp` that the Gradle build mirrors.
 *
 * Reading them from `Android.bp` instead of copying them keeps the Gradle build in sync when
 * upstream adds a resource directory, an extra R package or a source exclusion.
 */
data class AndroidBp(
    /** Resource directories in Soong order: later directories overlay earlier ones. */
    val resourceDirs: List<String>,
    /** Packages that aapt2 generates an extra R class for (`--extra-packages`). */
    val extraPackages: List<String>,
    /** Sources excluded from the app, as paths relative to the repository root. */
    val sourceExcludes: List<String>,
    /** Sub-manifests excluded from the merge, as paths relative to the repository root. */
    val manifestExcludes: List<String>,
) {
    companion object {
        fun parse(file: File): AndroidBp {
            // Drop whole-line comments; strings such as "//packages/apps/Dialer" stay intact.
            val text = file.readText().replace(Regex("(?m)^\\s*//.*$"), "")

            fun listAssignment(name: String): List<String> =
                strings(
                    Regex("""(?s)\b$name\s*=\s*\[(.*?)]""").find(text)?.groupValues?.get(1)
                        ?: error("Android.bp: $name not found")
                )

            fun listInModule(moduleName: String, property: String): List<String> {
                val start = text.indexOf("name: \"$moduleName\"")
                check(start >= 0) { "Android.bp: module $moduleName not found" }
                val body =
                    Regex("""(?s)\b$property\s*:\s*\[(.*?)]""").find(text, start)?.groupValues?.get(1)
                        ?: error("Android.bp: $moduleName.$property not found")
                return strings(body)
            }

            return AndroidBp(
                resourceDirs = listAssignment("RES_DIRS"),
                extraPackages =
                    listAssignment("EXTRA_PACKAGE_FLAGS")
                        .filter { it.startsWith("--extra-packages ") }
                        .map { it.removePrefix("--extra-packages ").trim() },
                sourceExcludes = listInModule("Dialer_app_defaults", "exclude_srcs"),
                manifestExcludes = listInModule("dialer_manifests", "exclude_srcs"),
            )
        }

        private fun strings(body: String): List<String> =
            Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
    }
}
