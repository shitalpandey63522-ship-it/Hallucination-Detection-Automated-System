# Stage 1: Build Spring Boot Jar
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B || true
COPY src src
RUN ./mvnw clean package -DskipTests

# Stage 2: Lightweight Alpine JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/hallucination-audit-*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8082
ENTRYPOINT ["java", "-Xmx384m", "-Xms256m", "-jar", "app.jar"]
