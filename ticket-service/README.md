# ticket-service

Match ticketing system with season subscriptions, VIP seat reservations, QR code generation, and ticket validation. Integrates with sports-service for match and category data.

## Technical Details

- **Port**: 8085
- **Database**: `ticket_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
ticket-service/src/main/java/com/wydad/digital/
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
mvn -B clean package --file ticket-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file ticket-service/pom.xml

# Run as part of full stack
docker compose up -d ticket-service
```

