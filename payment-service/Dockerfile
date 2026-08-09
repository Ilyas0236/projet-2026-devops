FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/payment-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]