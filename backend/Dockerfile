# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy the build files first so the dependency layer is cached until pom.xml changes
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is used by the container healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 appuser \
    && mkdir -p /data/media \
    && chown appuser:appuser /data/media

ARG JAR_FILE=target/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar
USER appuser

EXPOSE 8080

# Size the heap from the container limit so a small VPS is not starved
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
