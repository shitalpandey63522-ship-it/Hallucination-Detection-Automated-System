# Stage 1: Build Spring Boot Jar using Alpine Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B || true
COPY src src
RUN mvn clean package -DskipTests=true

# Stage 2: Lightweight Alpine JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/hallucination-audit-*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8082
ENTRYPOINT ["java", "-Xmx384m", "-Xms256m", "-jar", "app.jar"]
