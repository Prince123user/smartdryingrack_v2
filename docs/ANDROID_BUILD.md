# Android project and Compose compiler

Open the repository root in Android Studio: the directory containing `settings.gradle.kts`, `gradlew`, and `app/`. The native app's root project name is `SmartDryingRack`. The separate `my_app/android` directory is a Flutter scaffold; it is not the Gradle project containing this Compose app.

The native app uses Kotlin Android, kapt and the Compose compiler Gradle plugin, all pinned to **2.0.21**. The root declares `org.jetbrains.kotlin.plugin.compose` and the `app` module applies it. `buildFeatures.compose=true` remains enabled. The old Kotlin 1.9 / Compose compiler 1.5.14 configuration has been replaced; do not add that compiler extension setting back alongside the Kotlin 2 Compose plugin. The Compose BOM manages UI library versions, not compiler plugin activation.

Use Android Studio with support for AGP 8.13.2, the checked-in Gradle 8.13 wrapper, and JDK 17 for the verified workflow. Let Android Studio configure the Android SDK path locally.

After pulling this change:

1. Open the native project root and choose **Sync Project with Gradle Files**. Allow plugin/dependency downloads to complete.
2. Rebuild the app. From the project root, the equivalent checks are `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` (Windows: `gradlew.bat`).
3. If the build passes but the editor still reports “Composable function call without Compose plugin,” verify the file is under `app/src/main/java` and belongs to the imported `app` module, rather than an individually opened file or the Flutter scaffold. Confirm Gradle sync completed without errors, then reopen the project if its model remains stale.

The supplied screenshot shows the repeated messages in Android Studio’s Problems panel for `ControlScreen.kt`; the shown Gradle compile task reports a warning. The exact Android Studio version remains unknown. The repeated error was not independently reproduced in the cloud Gradle build. The explicit compiler-plugin configuration and the source cleanup are build-tested; this does not establish that a particular local IDE has successfully synced. If sync fails, its first error and the Android Studio version are needed to diagnose that local failure.

ControlScreen cleanup removes unused imports/helper code and redundant opt-ins/qualifiers, uses the auto-mirrored volume icon, and simplifies equivalent conditionals. Sound rules, firmware contracts and navigation are retained.
