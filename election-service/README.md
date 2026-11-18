# election-service

Democratic election system for club governance: candidate management, ballot creation, secure vote casting, real-time vote tallying, and result publication with notification integration.

## Technical Details

- **Port**: 8089
- **Database**: `elections_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
election-service/src/main/java/com/wydad/digital/
    controller/
    service/
    repository/
    model/
    dto/
    config/
    exception/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file election-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file election-service/pom.xml

# Run as part of full stack
docker compose up -d election-service
```

