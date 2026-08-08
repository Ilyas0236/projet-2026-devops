# ============================================
# RUNTIME JRE 21 (le JAR est buildé par Maven avant)
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Sécurité : utilisateur non-root
RUN addgroup -S wydad && adduser -S wydad -G wydad

# Copie le JAR pré-compilé
COPY target/content-service-*.jar app.jar
RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]