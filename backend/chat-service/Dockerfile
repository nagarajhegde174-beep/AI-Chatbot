# =====================================================================
# chat-service image.
#
# Built from THIS directory only. The build context is backend/chat-service,
# so auth-service, user-service, api-gateway and the infrastructure tier are
# physically unreachable from this build. That is the point: a service image
# that could pull in a sibling could compile against it, and then the services
# would not be independent (docs/ARCHITECTURE.md section 13, decision 012).
#
# Runs as a NON-ROOT user (docs/SECURITY.md section 12).
# =====================================================================

# ----------------------------------------------------------------- build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# `-DskipTests` only because the image build must not need a PostgreSQL. CI runs
# `mvn verify` with tests.
RUN mvn -B -ntp clean package -DskipTests

# ---------------------------------------------------------------- runtime ----
# JRE only: no Maven, no compiler, no shell tooling in the runtime image.
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system nexa && useradd --system --gid nexa --home /app --shell /usr/sbin/nologin nexa

WORKDIR /app

COPY --from=build --chown=nexa:nexa /build/target/chat-service-*.jar /app/chat-service.jar

USER nexa

# 8083 is this service's fixed port (docs/ARCHITECTURE.md section 1.2).
EXPOSE 8083

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

# Liveness only. Readiness needs to know whether the migration ran, which the
# application's own health endpoint reports.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8083/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/chat-service.jar"]

# =====================================================================
#  NO SECRETS IN THIS IMAGE.
#
#  Every credential is an environment variable or a mounted file
#  (docs/RULES.md section 6). Build arguments are public by definition.
#
#  This service is issued the RS256 PUBLIC key only. It never receives a
#  signing key, so it cannot mint a token for itself.
# =====================================================================