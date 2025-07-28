# Use smaller base image with build cache optimization
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /app

# Copy build files
COPY gradle/ gradle/
COPY *.gradle.kts gradle.properties libs.versions.toml ./

# Copy minimal resources for KordEx
COPY src/main/resources/translations/ src/main/resources/translations/

# Download dependencies
RUN --mount=type=cache,target=/root/.gradle \
    gradle dependencies --no-daemon

# Copy source and build
COPY src/ src/
COPY LICENSE ./
RUN --mount=type=cache,target=/root/.gradle \
    gradle distTar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Extract and setup
COPY --from=build /app/build/distributions/*.tar /tmp/
RUN tar -xf /tmp/*.tar --strip-components=1 && \
    rm /tmp/*.tar && \
    adduser -D appuser && \
    chown -R appuser:appuser /app

USER appuser
CMD ["./bin/Rocket-Manager"]
