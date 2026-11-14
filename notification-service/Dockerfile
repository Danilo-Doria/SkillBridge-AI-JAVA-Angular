# HU-104 WP-D — notification-service (JVM afinada). Sin puertos públicos: 8085 solo para actuator.
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/notification-service-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
WORKDIR /app
COPY --from=builder --chown=app:app /build/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /build/extracted/application/ ./
USER app
EXPOSE 8085
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss256k -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8085/actuator/health/readiness | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
