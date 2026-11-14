# ai-service — imagen JVM afinada para la VM compartida (HU-104, T040).
# Build: docker build -t skillbridge/ai-service ./ai-service
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests \
 && cp target/ai-service-*.jar /build/app.jar \
 && java -Djarmode=tools -jar /build/app.jar extract --layers --launcher --destination /build/extracted

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app -u 10001 app
# Capas de Spring Boot: dependencias primero para aprovechar la caché de Docker.
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./
USER app
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss256k -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom" \
    PORT=8082
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${PORT}/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
