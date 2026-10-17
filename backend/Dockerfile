# PayFlow backend — multi-stage production image (simulation only; no live payments).
# Build from backend/:  docker build -t payflow-backend:local .

# ---------- build ----------
FROM eclipse-temurin:21.0.12.1_1-jdk-alpine-3.24 AS build
WORKDIR /workspace

# Dependency layer (cache-friendly): wrapper + POM before sources
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw \
	&& ./mvnw -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -B -DskipTests package \
	&& cp target/payflow-backend-*.jar /workspace/app.jar \
	&& rm -f /workspace/app.jar.original

# ---------- runtime ----------
FROM eclipse-temurin:21.0.12.1_1-jre-alpine-3.24 AS runtime

LABEL org.opencontainers.image.title="payflow-backend" \
	org.opencontainers.image.description="PayFlow simulated payment platform backend" \
	org.opencontainers.image.source="https://github.com/local/payflow"

# JVM container memory strategy (override at runtime via JAVA_TOOL_OPTIONS).
ENV SPRING_PROFILES_ACTIVE=prod \
	JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=25.0 -Djava.security.egd=file:/dev/./urandom" \
	APP_HOME=/app

WORKDIR /app

# Non-root user; writable dir for optional local-profile JWT auto-generation only
RUN addgroup -S payflow && adduser -S -G payflow payflow \
	&& mkdir -p /app/local-secrets \
	&& chown -R payflow:payflow /app

COPY --from=build --chown=payflow:payflow /workspace/app.jar /app/app.jar

USER payflow

EXPOSE 8080

# Uses wget already present on eclipse-temurin Alpine JRE (no extra packages).
# Readiness (db+redis) — not liveness-only, so the container stays unhealthy until deps work.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
	CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
