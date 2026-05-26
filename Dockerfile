# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Run the application using a lightweight JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Crucial JVM memory optimizations for Render's 512MB free tier
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xss256k -XX:MaxRAMPercentage=70.0"

ENTRYPOINT ["java", "-jar", "app.jar"]