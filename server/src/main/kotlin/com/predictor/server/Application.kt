package com.predictor.server

import com.predictor.server.data.DatabaseFactory
import com.predictor.server.data.PredictionStore
import com.predictor.server.plugins.configureMonitoring
import com.predictor.server.plugins.configureRouting
import com.predictor.server.plugins.configureSerialization
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

/**
 * The single Ktor module. Everything the server does is wired up here from small,
 * focused `configure…` extension functions so each concern (JSON, logging, CORS,
 * routes) can be read — and explained in the article — on its own.
 */
fun Application.module() {
    val store = PredictionStore(DatabaseFactory.connect())

    configureSerialization()
    configureMonitoring()
    configureRouting(store)
}
