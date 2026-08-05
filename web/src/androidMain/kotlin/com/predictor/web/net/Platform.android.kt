package com.predictor.web.net

import com.predictor.web.BuildConfig

/**
 * The Android app talks to the backend over the network. The host is baked in at build time
 * from [BuildConfig.API_BASE_URL]: a device or Firebase-distribution build must point at the
 * deployed server, set with `-Ppredictor.apiBaseUrl=https://…` (or the `API_BASE_URL` env
 * var). The default, `http://10.0.2.2:8080`, is the special address the Android emulator uses
 * to reach `localhost` on the host machine, so local emulator dev needs no override.
 */
actual fun defaultBaseUrl(): String = BuildConfig.API_BASE_URL
