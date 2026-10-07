FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean verify
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S gas && adduser -S gas -G gas
COPY --from=build --chown=gas:gas /build/target/progetto-gas-0.1.0.jar app.jar
USER gas
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
