# syntax=docker/dockerfile:1
#
# NexaAI chat-service image.
#
# Two stages: a Maven build stage, and a JRE-only runtime stage that carries no
# build tooling and no source. The build context is THIS service's directory
# only, so this image can never contain another service's classes.

# ---------------------------------------------------------------- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer, so a source-only change reuses
# the cached Maven repository instead of downloading the world again.
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
# Tests are not run here: GitHub Actions is the test gate, and every service is
# built and tested in its own CI job before any image is published.
# See .github/workflows/backend-ci.yml.
RUN mvn -B -q clean package -DskipTests

# -------------------------------------------------------------- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as a non-root user. A container that does not need root does not get it.
RUN addgroup -S nexa && adduser -S nexa -G nexa

WORKDIR /app
COPY --from=build /build/target/chat-service.jar app.jar
RUN chown -R nexa:nexa /app

USER nexa

EXPOSE 8083

# Percentage-based heap so the container respects its own memory limit instead
# of the host's total memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

# Secrets are NEVER baked in. They arrive as environment variables from the
# deploy pipeline, see docs/RULES.md section 6.
ENV SPRING_PROFILES_ACTIVE=local

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget --quiet --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]