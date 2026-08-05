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

The Postgres JDBC driver is already on the server's classpath (`libs.postgresql` in
`server/build.gradle.kts`), so no build change is needed — a `jdbc:postgresql://…`
`DATABASE_URL` is enough and `DatabaseFactory` infers the driver from it. Exposed creates
the schema on first startup; swap in Flyway/Liquibase if you want managed migrations later.

### Deploying to Railway (or any Postgres host)

The repo ships a multi-stage `Dockerfile` that builds the Wasm frontend and the server
fat JAR and serves both. Railway prefers a `Dockerfile` over its autodetection, so the
deploy is reproducible. Add a Postgres database to the project and set on the server:

| Variable | Value |
|----------|-------|
| `DATABASE_URL` | `jdbc:postgresql://<host>:<port>/<db>` (Railway exposes host/port/db on the Postgres service) |
| `DATABASE_USER` | the database user |
| `DATABASE_PASSWORD` | the database password |

`PORT` is injected by the platform and already honoured in `Application.kt`. With no
`DATABASE_*` set the container falls back to the embedded H2 file at `/app/data`, which is
**ephemeral** on Railway — attach a volume or use Postgres for durable data.

> **Supabase note:** use the connection pooler in **session mode** (port `5432`, not the
> transaction-mode `6543`) and append `?sslmode=require` to the URL — the pooler is
> IPv4-friendly and session mode supports the prepared statements Exposed/HikariCP rely on.

## Seeding

`SeedData` (`data/SeedData.kt`) is still the single source of demo content. On startup
`PredictionStore` creates any missing tables and, **only if the `users` table is empty**,
inserts the seed tournaments, competitors, matches and predictions. A database that
already holds data — including predictions placed in a previous run — is left untouched.

## Tests

`ApiTest` gives every test its own throwaway in-memory database via
`DatabaseConfig.inMemory(name)`, so tests stay isolated and need no external service.
CI (`:server:test`) runs exactly as before.
