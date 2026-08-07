# ============================================
# ÉTAPE 1 : BUILD (compile le module auth-service)
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copie le POM parent
COPY pom.xml .

# Copie le POM et les sources du module
COPY auth-service/pom.xml auth-service/
COPY auth-service/src auth-service/src/

# Compile le module
RUN apk add --no-cache maven && \
    mvn -pl auth-service -am clean package -DskipTests

# ============================================
# ÉTAPE 2 : RUNTIME (image légère JRE)
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Utilisateur non-root
RUN addgroup -S wydad && adduser -S wydad -G wydad

# Copie le JAR depuis l'étape build
COPY --from=builder /app/auth-service/target/auth-service-*.jar app.jar

RUN chown wydad:wydad app.jar

USER wydad

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]