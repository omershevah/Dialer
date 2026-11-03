// Host-side annotation processor that generates the Dagger root components
// (Soong module: dialer-rootcomponentprocessor).

plugins {
    `java-library`
}

val repoRoot = rootProject.projectDir

sourceSets {
    main {
        java {
            setSrcDirs(listOf(repoRoot.resolve("java")))
            include(
                "com/android/dialer/rootcomponentgenerator/**/*.java",
                "com/android/dialer/inject/DialerRootComponent.java",
                "com/android/dialer/inject/DialerVariant.java",
                "com/android/dialer/inject/HasRootComponent.java",
                "com/android/dialer/inject/IncludeInDialerRoot.java",
                "com/android/dialer/inject/InstallIn.java",
                "com/android/dialer/inject/RootComponentGeneratorMetadata.java",
            )
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // JDK 17+ warns that release 8 is obsolete; that is expected here.
    options.compilerArgs.add("-Xlint:-options")
}

dependencies {
    // auto-common 0.10 is the last release whose MoreElements.getAnnotationMirror returns
    // Guava's Optional, which this processor uses.
    implementation("com.google.auto:auto-common:0.10")
    implementation("com.google.guava:guava:33.3.1-jre")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.google.dagger:dagger:2.57")
    implementation("javax.inject:javax.inject:1")
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    compileOnly("com.google.errorprone:error_prone_annotations:2.28.0")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}
