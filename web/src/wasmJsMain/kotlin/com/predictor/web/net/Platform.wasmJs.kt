package com.predictor.web.net

import kotlinx.browser.window

/** In the browser the API is served from the same origin as the page itself. */
actual fun defaultBaseUrl(): String = window.location.origin
