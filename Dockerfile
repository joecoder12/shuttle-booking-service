# Build stage: compile and package the Spring Boot jar (tests run in CI, not here).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /app/target/shuttle-booking-service-*.jar app.jar
USER app
# Hosting platforms such as Render inject PORT; application.yml falls back to 8080.
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
