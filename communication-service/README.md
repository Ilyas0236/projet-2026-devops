# communication-service

Real-time messaging platform: team group chats, direct messaging, and discussion threads. Built on WebSocket/STOMP for instant message delivery with JWT-authenticated STOMP connections.

## Technical Details

- **Port**: 8090
- **Database**: `communication_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
communication-service/src/main/java/com/wydad/digital/
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
mvn -B clean package --file communication-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file communication-service/pom.xml

# Run as part of full stack
docker compose up -d communication-service
```

