FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests || echo "No mvnw found, relying on parent CI build"

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/gamification-service-*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
