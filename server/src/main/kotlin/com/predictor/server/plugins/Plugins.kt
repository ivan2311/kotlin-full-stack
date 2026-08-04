package com.predictor.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

/**
 * JSON content negotiation, powered by the very same kotlinx.serialization models
 * that the shared module defines. The server never hand-writes JSON — it serialises
 * the shared types directly, which is exactly what the client deserialises them into.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
}

/** Request logging plus permissive CORS so the Wasm dev server can call the API. */
fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.local.uri.startsWith("/api") }
    }
    install(DefaultHeaders)
    install(CORS) {
        anyHost() // fine for a demo; tighten to known origins in production
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowHeader(HttpHeaders.ContentType)
    }
}
