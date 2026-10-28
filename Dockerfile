# Build stage: compiles and packages the jar. CI runs the tests, not this build, so tests are
# skipped here -- a slow or flaky test shouldn't be able to block an image from building.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn -B -DskipTests package

# Run stage: a plain JRE (no Maven, no JDK) keeps the final image small. Runs as a non-root user
# so a compromised app process can't do anything the container itself wasn't given permission to.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
