import SwiftUI

// The iOS application shell. It hosts a single Compose-rendered screen; everything
// inside it — every view, all the state, the scoring — is the shared Kotlin code
// compiled to a native framework by the :web module.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
