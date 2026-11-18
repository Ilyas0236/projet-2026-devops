# sports-service

Multi-discipline sports management: team rosters, player profiles, match convocations, match sheet generation, training session scheduling, and tactical media uploads via Cloudinary. Supports real-time group chat via WebSocket/STOMP.

## Technical Details

- **Port**: 8087
- **Database**: `sports_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
sports-service/src/main/java/com/wydad/digital/
    controller/
    service/
    repository/
    model/
    dto/
    config/
    exception/
    client/
    ws/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file sports-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file sports-service/pom.xml

# Run as part of full stack
docker compose up -d sports-service
```

