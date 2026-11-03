# Building with Gradle

The Dialer is part of the GrapheneOS platform and is normally built by Soong from
`Android.bp`. This repository also has a standalone Gradle build, so the app can be built
without a full GrapheneOS source tree.

```
./gradlew assembleRelease
```

The APK is written to `build/outputs/apk/release/Dialer-release.apk`. Every push also builds it
on GitHub Actions (workflow **Build APK**, artifact **Dialer**).

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

## Application ID and installing

By default the Gradle build is `dev.omershevah.dialer`, labelled **Phone Dev**, so it installs
next to the system Dialer (`com.android.dialer`, signed with the platform key). To make that
possible the build also:

- renames the content provider authorities and the `DIALER_ORIGIN` permission from
  `com.android.dialer.*` to the new ID (in the merged manifest, and through a generated
  `ConstantsImpl` that replaces `constants/aospdialer/ConstantsImpl.java`);
- sets the app and launcher label.

`-Pdialer.applicationId=...` and `-Pdialer.label=...` change these. With
`-Pdialer.applicationId=com.android.dialer` none of it happens and the build keeps the platform
identity.

After installing, choose it under **Settings → Apps → Default apps → Phone app**. It runs as a
regular app, so permissions that Android only grants to privileged system apps are not
available, and features that depend on them may not work.

## Signing

Release builds are signed with the key given by `DIALER_KEYSTORE_FILE`, `DIALER_KEYSTORE_PASSWORD`
and `DIALER_KEY_ALIAS` (environment variables or `-P` properties). Without them they are signed
with the debug key.

On GitHub Actions the key comes from two repository secrets (**Settings → Secrets and variables →
Actions**):

- `DIALER_KEYSTORE_BASE64`: the keystore file, base64-encoded.
- `DIALER_KEYSTORE_PASSWORD`: the keystore and key password. The key alias is `dialer`.

Android only installs an update signed with the same key as the installed app, so keep a backup of
the keystore. A new key means uninstalling the app (and its data) first.
