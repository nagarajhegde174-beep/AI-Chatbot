# =====================================================================
# auth-service image.
#
# Built from THIS directory only. The build context is backend/auth-service, so
# the seven sibling services and the infrastructure tier are physically
# unreachable from this build. That is the point: a service image that could
# pull in a sibling could compile against it, and then the services would not
# be independent (docs/ARCHITECTURE.md decision 012).
#
# Runs as a NON-ROOT user. A path-traversal or file-write bug should not become
# code execution as root (docs/SECURITY.md section 12).
# =====================================================================

# ----------------------------------------------------------------- build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Copy the POM first and resolve dependencies separately, so a source-only edit
# does not re-download the world.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests
# Tests are NOT skipped by default in CI: the backend workflow runs `mvn verify`
# with tests, and only the image rebuild after a green pipeline uses this stage.
# `-DskipTests` here is because the image build must not need a database.

# ---------------------------------------------------------------- runtime ----
# JRE only: no Maven, no compiler, no shell tooling in the runtime image.
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system nexa && useradd --system --gid nexa --home /app --shell /usr/sbin/nologin nexa

WORKDIR /app

COPY --from=build --chown=nexa:nexa /build/target/auth-service-*.jar /app/auth-service.jar

USER nexa

# 8081 is this service's fixed port (docs/ARCHITECTURE.md section 1.2).
EXPOSE 8081

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

# Liveness only. Readiness needs to know whether the migration ran, which the
# application's own health endpoint reports.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/auth-service.jar"]

# =====================================================================
#  NO SECRETS IN THIS IMAGE.
#
#  Every credential is an environment variable or a mounted file
#  (docs/RULES.md section 6). Build arguments are public by definition, so
#  none of them is a credential.
# =====================================================================