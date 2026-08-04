package com.predictor.server.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

/**
 * How to reach the database. Everything is read from the environment so nothing about
 * the deployment is baked into the build: point [url] at a Postgres instance in
 * production and the very same server persists there instead of the embedded H2 file.
 */
data class DatabaseConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int,
) {
    companion object {
        /**
         * The default: an embedded, file-backed H2 database next to the working dir, so
         * `./gradlew :server:run` keeps predictions across restarts with zero setup.
         * `AUTO_SERVER=TRUE` lets a second connection (e.g. an IDE) open the same file.
         */
        fun fromEnv(env: (String) -> String? = System::getenv): DatabaseConfig {
            val url = env("DATABASE_URL")
                ?: "jdbc:h2:file:./data/predictor;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1"
            return DatabaseConfig(
                url = url,
                driver = env("DATABASE_DRIVER") ?: driverFor(url),
                user = env("DATABASE_USER") ?: "",
                password = env("DATABASE_PASSWORD") ?: "",
                maxPoolSize = env("DATABASE_MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
            )
        }

        /** A throwaway in-memory database, one per [name] — used by tests for isolation. */
        fun inMemory(name: String): DatabaseConfig = DatabaseConfig(
            url = "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
            user = "",
            password = "",
            maxPoolSize = 1,
        )

        private fun driverFor(url: String): String = when {
            url.startsWith("jdbc:postgresql") -> "org.postgresql.Driver"
            else -> "org.h2.Driver"
        }
    }
}

/** Opens a pooled Exposed [Database] from a [DatabaseConfig]. */
object DatabaseFactory {

    fun connect(config: DatabaseConfig = DatabaseConfig.fromEnv()): Database {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                username = config.user
                password = config.password
                maximumPoolSize = config.maxPoolSize
                poolName = "predictor-db"
            },
        )
        return Database.connect(dataSource)
    }
}
