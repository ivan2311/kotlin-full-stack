# iOS app host

This folder is the thin native iOS shell. It contains no UI logic — every screen, all
state, and the scoring engine are the **shared Kotlin/Compose code** in
[`../web/src/commonMain`](../web/src/commonMain), compiled to a native framework by the
`:web` module and hosted here.

## How it fits together

- `:web` declares the `iosArm64` / `iosSimulatorArm64` targets and exposes the UI as a
  static framework named **`PredictorApp`** (see `web/build.gradle.kts`).
- The Kotlin entry point is `MainViewController()` in
  [`../web/src/iosMain/kotlin/com/predictor/web/MainViewController.kt`](../web/src/iosMain/kotlin/com/predictor/web/MainViewController.kt),
  which wraps the shared `App()` composable in a `UIViewController`.
- `ContentView.swift` bridges that `UIViewController` into SwiftUI via
  `UIViewControllerRepresentable`; `iOSApp.swift` is the app entry point.

## Building it

Building Apple targets requires **macOS with Xcode** — Kotlin/Native cannot compile
iOS on Linux, so this cannot be built in the Linux CI used by the rest of the project.
On a Mac:

1. Build the framework: `./gradlew :web:linkDebugFrameworkIosSimulatorArm64`
2. Create an Xcode app project in this folder (single-view SwiftUI app), add the two
   Swift files, and link the generated `PredictorApp.framework` (or wire it through the
   Kotlin Multiplatform Xcode integration / CocoaPods, whichever you prefer).
3. Point `defaultBaseUrl()` in
   [`../web/src/iosMain/kotlin/com/predictor/web/net/Platform.ios.kt`](../web/src/iosMain/kotlin/com/predictor/web/net/Platform.ios.kt)
   at your running backend, then run on a simulator or device.

The Swift here is deliberately minimal: its only job is to open a window and show the
shared Compose UI.
