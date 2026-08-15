# Doctor-Wang 后端最小镜像（1.6GB VPS 部署用）：
# 用本地构建的 jar 打包，绕开 Gradle-in-Docker 构建的内存压力（见 ADR-0007/recover-server.sh）。
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY backend-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
