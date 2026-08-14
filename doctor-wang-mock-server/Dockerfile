# syntax=docker/dockerfile:1
# ADR-0007: Java 21 LTS runtime (2026-08-14)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
# DEPLOY-001a：healthcheck 需要 curl（temurin 基础镜像不含）
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
