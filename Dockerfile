# -------- Stage 1: Build the app --------
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy the Maven project
COPY pom.xml .
COPY src ./src

# Build the Spring Boot app (skip tests for faster build)
RUN mvn clean package -DskipTests

# -------- Stage 2: Run the app --------
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy the jar file from builder stage
COPY --from=builder /app/target/saffaricarrers-0.0.1-SNAPSHOT.jar app.jar

# Expose the Spring Boot default port
EXPOSE 8080

# Run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
