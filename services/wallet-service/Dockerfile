# Multi-stage build for Spring Boot application
# Stage 1: Build the application
FROM maven:3.9.4-eclipse-temurin-21 AS build

WORKDIR /app

# Copy pom.xml and download dependencies (for better caching)
COPY . .


# Copy source code and build

RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
# RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN addgroup -S appuser && adduser -S appuser -G appuser

# Copy the JAR file from build stage
COPY --from=build /app/services/wallet-service/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8083/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]