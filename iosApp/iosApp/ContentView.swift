import SwiftUI
import UIKit
import PredictorApp // the framework produced by the :web module (baseName = "PredictorApp")

// Bridges the shared Compose UI into SwiftUI. `MainViewController()` is the Kotlin
// entry point in web/src/iosMain/.../MainViewController.kt; the Kotlin compiler exposes
// the top-level function on the generated `MainViewControllerKt` object.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
    }
}
