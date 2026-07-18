FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy root POM and all module POMs for Maven dependency resolution
COPY pom.xml .
COPY careflow-common/pom.xml careflow-common/pom.xml
COPY careflow-eureka-server/pom.xml careflow-eureka-server/pom.xml
COPY careflow-api-gateway/pom.xml careflow-api-gateway/pom.xml
COPY careflow-identity-service/pom.xml careflow-identity-service/pom.xml
COPY careflow-patient-service/pom.xml careflow-patient-service/pom.xml
COPY careflow-appointment-service/pom.xml careflow-appointment-service/pom.xml
COPY careflow-queue-service/pom.xml careflow-queue-service/pom.xml
COPY careflow-notification-service/pom.xml careflow-notification-service/pom.xml
COPY careflow-consultation-service/pom.xml careflow-consultation-service/pom.xml
COPY careflow-prescription-service/pom.xml careflow-prescription-service/pom.xml
COPY careflow-emr-service/pom.xml careflow-emr-service/pom.xml
COPY careflow-lab-service/pom.xml careflow-lab-service/pom.xml
COPY careflow-ai-service/pom.xml careflow-ai-service/pom.xml

# Install parent POM into Maven local repo
RUN mvn -N install -DskipTests -B -q

# Build careflow-common (shared library)
COPY careflow-common/src careflow-common/src
RUN mvn -pl careflow-common install -DskipTests -B -q

# Build service
COPY careflow-patient-service/src careflow-patient-service/src
RUN mvn -pl careflow-patient-service package -DskipTests -B -q

# Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/careflow-patient-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
