# syntax=docker/dockerfile:1.7
FROM gradle:9.6.1-jdk25-alpine AS build
WORKDIR /app

# Copy build files
COPY gradle/ gradle/
COPY *.gradle.kts gradle.properties libs.versions.toml ./

# Copy minimal resources for KordEx
COPY src/main/resources/translations/ src/main/resources/translations/

# Download dependencies, jars included, so they land in their own cached layer
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    --mount=type=cache,target=/app/.gradle,sharing=locked \
    gradle warmupDependencies --no-daemon

# Copy source and build
COPY src/ src/
COPY LICENSE ./

# in-process compilation skips the cost of forking a Kotlin daemon for a one-shot build
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    --mount=type=cache,target=/app/.gradle,sharing=locked \
    gradle installDist --no-daemon -Pkotlin.compiler.execution.strategy=in-process

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/build/install/Rocket-Manager/ ./

# The JVM writes a class-data archive on the first boot and reuses it on later starts.
# It is rebuilt automatically whenever the classpath changes.
ENV JAVA_OPTS="-XX:+AutoCreateSharedArchive -XX:SharedArchiveFile=/app/cds/app.jsa"

RUN adduser -D appuser && \
    mkdir -p /app/cds /app/logs && \
    chown -R appuser:appuser /app

USER appuser
CMD ["./bin/Rocket-Manager"]
