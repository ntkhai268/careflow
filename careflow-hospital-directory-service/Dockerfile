FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

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
COPY careflow-hospital-directory-service/pom.xml careflow-hospital-directory-service/pom.xml

RUN mvn -N install -DskipTests -B -q

COPY careflow-common/src careflow-common/src
RUN mvn -pl careflow-common install -DskipTests -B -q

COPY careflow-hospital-directory-service/src careflow-hospital-directory-service/src
RUN mvn -pl careflow-hospital-directory-service package -DskipTests -B -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/careflow-hospital-directory-service/target/*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
