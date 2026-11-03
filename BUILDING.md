# Building with Gradle

The Dialer is part of the GrapheneOS platform and is normally built by Soong from
`Android.bp`. This repository also has a standalone Gradle build, so the app can be built
without a full GrapheneOS source tree.

```
./gradlew assembleDebug
```

The APK is written to `build/outputs/apk/debug/Dialer-debug.apk`. Every push also builds it on
GitHub Actions (workflow **Build APK**, artifact **Dialer-debug**).

## Requirements

- JDK 17 or newer.
- Android SDK with `ANDROID_HOME` set, or `sdk.dir` in `local.properties`. Gradle downloads
  the platform and build tools it needs.
- Network access to `dl.google.com`, `maven.google.com`, Maven Central, `services.gradle.org`
  and `raw.githubusercontent.com`.

## How it maps to Android.bp

`Android.bp` stays the source of truth. The Gradle build reads these lists from it at
configuration time, so upstream changes to them need no Gradle edits:

| Soong                           | Gradle                                                     |
| ------------------------------- | ---------------------------------------------------------- |
| `resource_dirs` (overlay order) | `mergeDebugDialerResources` merges them into one directory, later directories overriding earlier ones, as aapt2 overlays do |
| `aaptflags --extra-packages`    | `generateDebugExtraRClasses` writes an `R` class into each extra package |
| `additional_manifests`          | `mergeDialerManifests` merges every sub-package manifest into the main one |
| `exclude_srcs`                  | Java/Kotlin source excludes                                |
| `dialer-rootcomponentprocessor` | `:rootcomponentprocessor` subproject (`gradle/rootcomponentprocessor`) |
| `sdk_version: "system_current"` | A full framework `android.jar` (hidden and system APIs, from [Reginer/aosp-android-jar](https://github.com/Reginer/aosp-android-jar), android-17.0.0_r1) is put ahead of the SDK on javac's boot classpath. Pass `-Pdialer.frameworkJar=/path/to/android.jar` to use another one, such as `out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar` from a platform build. |

Platform libraries that are not published to Maven live in `third_party/`:

- `third_party/android-common`: `MoreCloseables` from `frameworks/ex/common`.
- `third_party/libbackup`: a source-compatible stand-in for `external/libbackup`.

The remaining `static_libs` and `plugins` map to Maven artifacts in `build.gradle.kts`.

## Installing the APK

The Gradle build keeps the package name `com.android.dialer` and is signed with the Gradle
debug key, not the platform `shared` key. On a device where Dialer is already a system app
(including GrapheneOS), it can't be installed as an update of the system Dialer. Install it on
an emulator or a device without that system app, or sign it with the matching platform key.
Privileged permissions are only granted to the app when it is installed in the system image.
