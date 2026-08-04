package com.predictor.web

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS entry point. Swift calls this from its `UIViewControllerRepresentable`
 * (or an `AppDelegate`) to host the shared Compose [App] inside a native
 * `UIViewController`. Every screen below it is the same common code the Android and
 * web apps run.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
