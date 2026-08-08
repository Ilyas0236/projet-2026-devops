# ============================================
# RUNTIME JRE 21
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S wydad && adduser -S wydad -G wydad

COPY target/auth-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]