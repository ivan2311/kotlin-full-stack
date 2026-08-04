package com.predictor.web.net

/**
 * The iOS app talks to the backend over the network. Point this at your API host;
 * `localhost` works for a simulator hitting a server on the same machine.
 */
actual fun defaultBaseUrl(): String = "http://localhost:8080"
