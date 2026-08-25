FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/communication-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "app.jar"]
