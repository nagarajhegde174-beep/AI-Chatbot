# =====================================================================
# api-gateway image.
#
# Built from THIS directory only. The build context is backend/api-gateway,
# so every service and the infrastructure tier are physically unreachable from
# this build (docs/ARCHITECTURE.md section 13, decision 012).
#
# Runs as a NON-ROOT user (docs/SECURITY.md section 12).
# =====================================================================

# ----------------------------------------------------------------- build ----
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# `-DskipTests` because the image build must not need to bind a port. CI runs
# `mvn verify` with tests, including the ones that start a real socket.
RUN mvn -B -ntp clean package -DskipTests

# ---------------------------------------------------------------- runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system nexa && useradd --system --gid nexa --home /app --shell /usr/sbin/nologin nexa

WORKDIR /app

COPY --from=build --chown=nexa:nexa /build/target/api-gateway-*.jar /app/api-gateway.jar

USER nexa

# 8080 is the platform's public port (docs/ARCHITECTURE.md section 1.2). It is
# the only port exposed to the outside; services are never published directly.
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/api-gateway.jar"]

# =====================================================================
#  NO SECRETS IN THIS IMAGE.
#
#  Every credential is an environment variable or a mounted file
#  (docs/RULES.md section 6). Build arguments are public by definition.
#
#  The gateway is issued the RS256 PUBLIC key only. It has no signing key and
#  therefore cannot issue a token; if it could, there would be two places a
#  token could be minted, and only one of them reviewed.
# =====================================================================