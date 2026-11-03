// Standalone Gradle build for the GrapheneOS Dialer. See BUILDING.md.
//
// Mirrors the "Dialer" android_app in Android.bp. Lists that Soong owns (resource directories,
// --extra-packages, source and manifest exclusions) are read from Android.bp at configuration
// time rather than copied here.

import dialer.build.AndroidBp
import dialer.build.GenerateExtraRClassesTask
import dialer.build.MergeAdditionalManifestsTask
import dialer.build.MergeOverlayResourcesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.protobuf")
}

val bp = AndroidBp.parse(file("Android.bp"))
val appNamespace = "com.android.dialer"

// --- Framework classes ------------------------------------------------------------------------
// Soong compiles against sdk_version "system_current", and GrapheneOS changes also use hidden
// APIs. The public SDK's android.jar has neither, so javac gets a full framework android.jar on
// its boot classpath, ahead of the SDK one. Override with -Pdialer.frameworkJar=/path/android.jar.

val frameworkJar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    frameworkJar("aosp-android-jar:android-37:17.0.0_r1") {
        artifact {
            name = "android"
            type = "jar"
            extension = "jar"
        }
    }
}

val frameworkClasses: FileCollection =
    providers.gradleProperty("dialer.frameworkJar").orNull?.let { files(it) } ?: frameworkJar

// --- Soong emulation --------------------------------------------------------------------------

// additional_manifests: every sub-package AndroidManifest.xml, merged into the main one.
val mergeDialerManifests =
    tasks.register<MergeAdditionalManifestsTask>("mergeDialerManifests") {
        mainManifest.set(file("AndroidManifest.xml"))
        additionalManifests.from(
            fileTree(".") {
                include("java/**/AndroidManifest.xml", "assets/**/AndroidManifest.xml")
                exclude(bp.manifestExcludes)
            }
        )
        outputManifest.set(layout.buildDirectory.file("generated/dialer/manifest/AndroidManifest.xml"))
        workDir.set(layout.buildDirectory.dir("tmp/mergeDialerManifests"))
    }
tasks.named("preBuild") { dependsOn(mergeDialerManifests) }

// Protos import each other as "java/com/android/...", so they are staged under a root that
// contains a "java" directory instead of pointing protoc at the repository root.
val stageDialerProtos =
    tasks.register<Sync>("stageDialerProtos") {
        from("java") { include("**/*.proto") }
        into(layout.buildDirectory.dir("generated/dialer/proto-src/java"))
    }

android {
    namespace = appNamespace
    compileSdk = 36

    defaultConfig {
        applicationId = appNamespace
        minSdk = 24
        targetSdk = 30
        versionCode = 2900000
        versionName = "23.0"
    }

    buildFeatures {
        aidl = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        encoding = "UTF-8"
    }

    useLibrary("org.apache.http.legacy")

    sourceSets.getByName("main") {
        manifest.srcFile(mergeDialerManifests.get().outputManifest.get().asFile)
        java.setSrcDirs(
            listOf("java", "third_party/libbackup/java", "third_party/android-common/java")
        )
        java.include(
            "com/android/**/*.java",
            "com/android/**/*.kt",
            "com/google/android/libraries/backup/**/*.java",
        )
        java.exclude(bp.sourceExcludes.map { it.removePrefix("java/") })
        aidl.setSrcDirs(listOf("java"))
        // Resources come from mergeDialerResources (registered per variant below).
        res.setSrcDirs(emptyList<String>())
        (this as ExtensionAware).extensions.getByName<SourceDirectorySet>("proto").apply {
            setSrcDirs(emptyList<String>())
            srcDir(stageDialerProtos.map { it.destinationDir.parentFile })
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug key so the release APK installs for testing; replace for real releases.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar(Char::uppercase)

        // resource_dirs with Soong's overlay order applied.
        val mergeRes =
            tasks.register<MergeOverlayResourcesTask>("merge${cap}DialerResources") {
                resourceDirs.from(bp.resourceDirs.map { file(it) })
            }
        variant.sources.res?.addGeneratedSourceDirectory(mergeRes, MergeOverlayResourcesTask::outputDir)

        // aaptflags --extra-packages.
        val extraR =
            tasks.register<GenerateExtraRClassesTask>("generate${cap}ExtraRClasses") {
                symbolListDir.set(
                    layout.buildDirectory.dir("intermediates/runtime_symbol_list/${variant.name}")
                )
                namespace.set(appNamespace)
                packages.set(bp.extraPackages)
                kotlinSources.from(fileTree("java") { include("**/*.kt") })
                dependsOn("process${cap}Resources")
            }
        variant.sources.java?.addGeneratedSourceDirectory(extraR, GenerateExtraRClassesTask::outputDir)
    }
}

tasks.withType<JavaCompile>().configureEach {
    inputs.files(frameworkClasses)
        .withPropertyName("frameworkJar")
        .withNormalizer(ClasspathNormalizer::class.java)
    options.compilerArgs.addAll(listOf("-Xlint:-options", "-Xmaxerrs", "1000"))
    // Prepended at execution time so it lands ahead of the SDK android.jar that AGP configures.
    doFirst {
        options.bootstrapClasspath = frameworkClasses + (options.bootstrapClasspath ?: files())
    }
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.25.5" }
    generateProtoTasks {
        all().configureEach {
            builtins { maybeCreate("java").option("lite") }
        }
    }
}

dependencies {
    // android-support-* (the platform still ships the pre-AndroidX support library).
    val support = "28.0.0"
    implementation("com.android.support:appcompat-v7:$support")
    implementation("com.android.support:cardview-v7:$support")
    implementation("com.android.support:design:$support")
    implementation("com.android.support:recyclerview-v7:$support")
    implementation("com.android.support:support-annotations:$support")
    implementation("com.android.support:support-core-ui:$support")
    implementation("com.android.support:support-dynamic-animation:$support")
    implementation("com.android.support:support-v13:$support")
    implementation("com.android.support:support-v4:$support")
    implementation("com.android.support:transition:$support")

    // androidx.datastore_*, kotlinx_coroutines*
    implementation("androidx.datastore:datastore:1.1.7")
    implementation("androidx.datastore:datastore-core:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

    // dialer-common-m2-target-deps, dialer-glide-target-deps and friends
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.dagger:dagger:2.48.1")
    implementation("javax.inject:javax.inject:1")
    implementation("com.github.bumptech.glide:glide:4.8.0") // last release on the support library
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
    implementation("com.googlecode.libphonenumber:geocoder:2.+") // pulls the matching libphonenumber
    implementation("com.android.volley:volley:1.2.1")
    // Voicemail transcription client. The checked-in stub (VoicemailTranscriptionServiceGrpc) was
    // generated by protoc-gen-grpc-java 1.0.3 and uses MethodDescriptor.create and
    // usePlaintext(boolean); 1.21.1 is the newest release that still has both.
    val grpc = "1.21.1"
    implementation("io.grpc:grpc-core:$grpc")
    implementation("io.grpc:grpc-okhttp:$grpc")
    implementation("io.grpc:grpc-stub:$grpc")
    implementation("io.grpc:grpc-protobuf-lite:$grpc") {
        // Pre-javalite protobuf runtime; protobuf-javalite above replaces it.
        exclude(group = "com.google.protobuf", module = "protobuf-lite")
    }
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.apache.james:apache-mime4j-core:0.8.9")
    implementation("org.apache.james:apache-mime4j-dom:0.8.9")
    implementation("commons-io:commons-io:2.6")
    implementation("me.leolin:ShortcutBadger:1.1.22@aar")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.errorprone:error_prone_annotations:2.28.0")
    compileOnly("com.google.auto.value:auto-value-annotations:1.10.4")

    // plugins: (annotation processors)
    annotationProcessor("com.google.auto.value:auto-value:1.10.4")
    annotationProcessor("com.google.dagger:dagger-compiler:2.48.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.8.0")
    annotationProcessor(project(":rootcomponentprocessor"))
}
