FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests || echo "No mvnw found, relying on parent CI build"
# En fait, dans ce projet, le CI build le JAR via le POM parent ou un mvn local,
# puis le Dockerfile copie le JAR compilé.

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/ticket-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
