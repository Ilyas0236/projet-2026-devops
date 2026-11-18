# api-gateway

Central API Gateway built with Spring Cloud Gateway. Routes all client requests to the appropriate microservice, handles JWT validation, CORS configuration, and rate limiting. Single public entry point for the entire platform.

## Technical Details

- **Port**: 8080
- **Database**: None (stateless proxy)
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
api-gateway/src/main/java/com/wydad/digital/
    config/
    filter/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file api-gateway/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file api-gateway/pom.xml

# Run as part of full stack
docker compose up -d api-gateway
```

