# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN test -n "${MODULE}"
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -pl "${MODULE}" -am package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ARG MODULE
RUN addgroup -S careflow && adduser -S -G careflow careflow
COPY --from=build --chown=careflow:careflow /workspace/${MODULE}/target/${MODULE}-1.0.0-SNAPSHOT.jar app.jar
USER careflow:careflow
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
