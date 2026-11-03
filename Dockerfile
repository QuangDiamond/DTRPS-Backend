# Build the jar first: mvn package -DskipTests
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home dtrps
COPY target/dtrps-backend-0.0.1-SNAPSHOT.jar /app/app.jar
USER dtrps
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
