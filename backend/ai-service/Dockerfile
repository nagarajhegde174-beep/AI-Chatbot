# =====================================================================
# ai-service image.
#
# Built from THIS directory only. The build context is backend/ai-service,
# so auth-service, user-service, chat-service, api-gateway and the
# infrastructure tier are physically unreachable from this build. That is
# the point: a service image that could pull in a sibling could compile
# against it, and then the services would not be independent
# (docs/ARCHITECTURE.md section 13, decision 012).
#
# Runs as a NON-ROOT user (docs/SECURITY.md section 12).
#
# STATELESS, so this image has no database driver expectations, no
# migration step and no volume. `docker stop` loses nothing, which is the
# practical reason CI rule R5 forbids a datasource here rather than merely
# discouraging one.
# =====================================================================

# ----------------------------------------------------------------- build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# `-DskipTests` for a reason specific to THIS service: its tests need neither a
# database nor a network nor a provider credential, so CI runs them with no
# setup at all. Skipping here is about image build time, not about avoiding a
# missing dependency.
RUN mvn -B -ntp clean package -DskipTests

# ---------------------------------------------------------------- runtime ----
# JRE only: no Maven, no compiler, no shell tooling in the runtime image.
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system nexa && useradd --system --gid nexa --home /app --shell /usr/sbin/nologin nexa

WORKDIR /app

COPY --from=build --chown=nexa:nexa /build/target/ai-service-*.jar /app/ai-service.jar

USER nexa

# 8084 is this service's fixed port (docs/ARCHITECTURE.md section 1.2).
EXPOSE 8084

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

# Liveness only, deliberately. Readiness that gated on "has a provider key"
# would report NOT READY on any deployment that has not configured one --
# including every development machine -- and the service would never receive
# traffic. A provider with no credential is a supported state that
# /internal/v1/ai/models reports with a reason.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/ai-service.jar"]

# =====================================================================
#  NO SECRETS IN THIS IMAGE.
#
#  Every credential is an environment variable or a mounted file
#  (docs/RULES.md section 6). Build arguments are public by definition.
#
#  This service holds the platform's AI PROVIDER CREDENTIALS, which are
#  billable, so the following are properties of the code and not merely of
#  this file:
#
#    - NEXA_AI_JWT_PUBLIC_KEY is the RS256 PUBLIC key. There is
#      deliberately no private-key variable for this service: it verifies
#      tokens and never signs one, so it cannot mint a token for itself.
#    - Provider keys are read at startup, never logged, never returned by
#      any endpoint, and never reach the browser.
#
#  The image must not be published to a public registry. It is not a
#  secret in the usual sense, but it is the container that reads the
#  provider keys.
# =====================================================================