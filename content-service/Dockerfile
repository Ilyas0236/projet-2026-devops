FROM eclipse-temurin:21-jre-alpine

# ⬇️ AJOUTE CECI
RUN apk add --no-cache wget

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/content-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]