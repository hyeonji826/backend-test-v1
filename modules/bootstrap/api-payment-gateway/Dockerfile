# syntax=docker/dockerfile:1

# -------- Build stage --------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache Gradle wrapper and dependencies first
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle.properties ./
RUN chmod +x gradlew || true

# Copy sources
COPY modules modules

# Build only the API module bootJar to keep it faster
RUN ./gradlew :modules:bootstrap:api-payment-gateway:bootJar -x test --no-daemon

# -------- Runtime stage --------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy the built jar
COPY --from=build /workspace/modules/bootstrap/api-payment-gateway/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
