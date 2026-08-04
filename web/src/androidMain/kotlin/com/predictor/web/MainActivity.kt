package com.predictor.web

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * The Android entry point. It does nothing but host the shared Compose [App] — the
 * same composables the iOS and web apps render. All the screens, state and scoring
 * live in common code; this class only supplies the `Activity` window.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
