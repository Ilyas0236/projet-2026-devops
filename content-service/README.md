# content-service

Manages all public-facing content: news articles, match events, sports calendar, press releases, and media. Supports editorial workflows and integrates with sports-service for match data resolution.

## Technical Details

- **Port**: 8082
- **Database**: `content_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
content-service/src/main/java/com/wydad/digital/
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
mvn -B clean package --file content-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file content-service/pom.xml

# Run as part of full stack
docker compose up -d content-service
```

