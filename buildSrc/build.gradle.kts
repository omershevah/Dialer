plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

// Keep in sync: AGP 8.13.0 ships with Android Tools 31.13.0.
val agpVersion = "8.13.0"
val androidToolsVersion = "31.13.0"

dependencies {
    implementation("com.android.tools.build:gradle:$agpVersion")
    implementation("com.android.tools.build:manifest-merger:$androidToolsVersion")
    implementation("com.android.tools:common:$androidToolsVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.5")
}
