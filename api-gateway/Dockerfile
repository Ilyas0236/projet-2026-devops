FROM eclipse-temurin:21-jre-alpine

# ⬇️ AJOUTE CECI : wget pour le healthcheck Docker
RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/api-gateway-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]