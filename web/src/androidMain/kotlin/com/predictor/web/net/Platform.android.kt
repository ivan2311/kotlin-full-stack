package com.predictor.web.net

/**
 * The Android app talks to the backend over the network. `10.0.2.2` is the special
 * address the Android emulator uses to reach `localhost` on the host machine; point
 * this at your real API host for a device build.
 */
actual fun defaultBaseUrl(): String = "http://10.0.2.2:8080"
