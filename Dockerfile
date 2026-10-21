# syntax=docker/dockerfile:1

# --- Stage 1: build the React SPA ---
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci || npm install
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot jar (Java 25) ---
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY src ./src
# Bundle the built SPA into the jar's static resources.
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
# Strip Windows-specific dev settings (TLS truststore + local JDK path) so the
# Linux build uses its own CA store and auto-detects the Temurin 25 toolchain.
RUN sed -i '/trustStoreType=Windows-ROOT/d; /java.installations.paths/d' gradle.properties
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test
RUN cp build/libs/expense-tracker-*.jar /app/app.jar

# --- Stage 3: slim runtime ---
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /app/app.jar ./app.jar
# Keep heap small for free-tier memory; virtual threads carry the concurrency.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
# SQLite data store (financial data + audit log in one DB). The directory is
# created on startup; mount a persistent volume at /data (e.g.
# -v expense-data:/data) or the DB — and its WAL sidecars — are lost on
# container restart.
ENV DATA_DB_PATH="/data/tracker.db"
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
