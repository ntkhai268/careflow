FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN test -n "${MODULE}" && mvn -pl "${MODULE}" -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S careflow && adduser -S -G careflow -u 10001 careflow
WORKDIR /app
ARG MODULE
COPY --from=build --chown=careflow:careflow \
    /workspace/${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080 8081 8761
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
