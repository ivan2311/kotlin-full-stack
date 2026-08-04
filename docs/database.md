# Database

The server persists its data in a SQL database via [Exposed](https://github.com/JetBrains/Exposed)
(the SQL DSL, not the DAO). Out of the box it uses an embedded **H2** database written
to a local file, so nothing needs installing — `./gradlew :server:run` just works and
keeps your predictions across restarts. The same server points at Postgres (or any
JDBC database) with nothing but environment variables.

## What lives where

| File | Role |
|------|------|
| `server/.../data/Tables.kt` | The Exposed schema — `users`, `tournaments`, `competitors`, `matches`, `predictions`. Mirrors the shared domain model one-to-one. |
| `server/.../data/DatabaseFactory.kt` | Reads `DatabaseConfig` from the environment and opens a HikariCP-pooled `Database`. |
| `server/.../data/PredictionStore.kt` | The data layer. Same public methods the routes always called; each runs in a `transaction`. Creates the schema on startup and seeds it from `SeedData` **only when the database is empty**. |

The routes, the API contract, and the client are all unchanged — the store's method
signatures are identical to the old in-memory version, which is exactly what the
`:shared` single-source-of-truth boundary is meant to allow.

## Configuration

Everything is read from the environment (`DatabaseConfig.fromEnv`); nothing about a
deployment is baked into the build:

| Variable | Default | Notes |
|----------|---------|-------|
| `DATABASE_URL` | `jdbc:h2:file:./data/predictor;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1` | Any JDBC URL. |
| `DATABASE_DRIVER` | inferred from the URL (`org.h2.Driver`, `org.postgresql.Driver`) | Override if inference is wrong. |
| `DATABASE_USER` | *(empty)* | |
| `DATABASE_PASSWORD` | *(empty)* | |
| `DATABASE_MAX_POOL_SIZE` | `10` | Hikari pool size. |

The default H2 file lives at `./data/predictor.mv.db` relative to the working directory.
Because `:server:run` runs from the repo root, that's `<repo>/data/` — which is
gitignored. Delete the `data/` directory to start from a fresh, re-seeded database.

### Pointing at Postgres

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/predictor"
export DATABASE_USER="predictor"
export DATABASE_PASSWORD="…"
./gradlew :server:run
```

Add the Postgres JDBC driver to `server/build.gradle.kts`
(`implementation("org.postgresql:postgresql:<version>")`) so the driver class is on the
classpath. Exposed creates the schema on first startup; swap in Flyway/Liquibase if you
want managed migrations later.

## Seeding

`SeedData` (`data/SeedData.kt`) is still the single source of demo content. On startup
`PredictionStore` creates any missing tables and, **only if the `users` table is empty**,
inserts the seed tournaments, competitors, matches and predictions. A database that
already holds data — including predictions placed in a previous run — is left untouched.

## Tests

`ApiTest` gives every test its own throwaway in-memory database via
`DatabaseConfig.inMemory(name)`, so tests stay isolated and need no external service.
CI (`:server:test`) runs exactly as before.
