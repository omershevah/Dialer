// Standalone Gradle build for the GrapheneOS Dialer.
//
// The platform build (Soong, Android.bp) is unchanged and remains the reference. This build reads
// resource directories, extra R packages and exclusions from Android.bp, so it follows upstream
// changes without edits. See BUILDING.md.

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // android.jar with hidden and system APIs, built from android-17.0.0_r1.
        // Pinned to a commit of https://github.com/Reginer/aosp-android-jar.
        ivy {
            name = "aospAndroidJar"
            url = uri("https://raw.githubusercontent.com/Reginer/aosp-android-jar/262f6ae931160011572a5adfe4ec6302585e8f60/")
            patternLayout { artifact("[module]/[artifact].[ext]") }
            metadataSources { artifact() }
            content { includeGroup("aosp-android-jar") }
        }
    }
}

rootProject.name = "Dialer"

include(":rootcomponentprocessor")
project(":rootcomponentprocessor").projectDir = file("gradle/rootcomponentprocessor")
