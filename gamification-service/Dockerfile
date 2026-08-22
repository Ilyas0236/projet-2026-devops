FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/gamification-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
