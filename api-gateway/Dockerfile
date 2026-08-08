
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/api-gateway-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]