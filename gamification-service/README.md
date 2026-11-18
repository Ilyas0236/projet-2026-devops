# gamification-service

Fan engagement engine: loyalty points accumulation, achievement badges, fan leaderboards, and challenge campaigns. Integrates with content-service for event-driven point attribution and notification-service for reward alerts.

## Technical Details

- **Port**: 8088
- **Database**: `wydad_gamification`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
gamification-service/src/main/java/com/wydad/digital/
    controller/
    service/
    repository/
    model/
    dto/
    config/
    exception/
    client/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file gamification-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file gamification-service/pom.xml

# Run as part of full stack
docker compose up -d gamification-service
```

