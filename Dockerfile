# syntax=docker/dockerfile:1

# ── Build stage ───────────────────────────────────────────────────────────────
# Builds two things the runtime needs, in one pass:
#   1. the Wasm web frontend  → web/build/dist/wasmJs/productionExecutable
#   2. the server fat JAR      → server/build/libs/server-all.jar
# JDK 21 matches CI; the Gradle wrapper provides the Kotlin/Compose/Wasm/Node
# toolchains, so nothing else needs installing.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the whole project (the .dockerignore keeps build outputs/caches out).
COPY . .

# The Wasm distribution downloads a Node/Yarn toolchain via the wrapper, so the
# build stage needs network access (Railway's builder provides it).
RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon --no-configuration-cache \
        :web:wasmJsBrowserDistribution \
        :server:buildFatJar

# ── Runtime stage ─────────────────────────────────────────────────────────────
# Only a JRE plus the two build outputs, laid out so the server's locateWebDist()
# (which walks up from the working directory) finds the frontend at
# web/build/dist/wasmJs/productionExecutable relative to /app.
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

COPY --from=build /app/server/build/libs/server-all.jar /app/server/build/libs/server-all.jar
COPY --from=build /app/web/build/dist/wasmJs/productionExecutable /app/web/build/dist/wasmJs/productionExecutable

# The app reads PORT (default 8080) and binds 0.0.0.0 in code; Railway injects PORT.
EXPOSE 8080
CMD ["java", "-jar", "server/build/libs/server-all.jar"]
