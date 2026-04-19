# Multi-stage build: compile with Maven, run with JRE 21
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /workspace/target/medlab-billing-service-1.0.0.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]