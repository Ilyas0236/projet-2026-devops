# notification-service

Centralized notification hub delivering real-time in-app alerts to users. Consumed by other services via internal API to trigger user-facing notifications for purchases, elections, and system events.

## Technical Details

- **Port**: 8086
- **Database**: `notification_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
notification-service/src/main/java/com/wydad/digital/
    controller/
    service/
    repository/
    model/
    dto/
    config/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file notification-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file notification-service/pom.xml

# Run as part of full stack
docker compose up -d notification-service
```

