# auth-service

Handles user registration, authentication (JWT), role-based access control (8 roles), profile management, KYC document verification via Cloudinary, and admin account seeding. Integrates with Redis for token blacklisting and session management.

## Technical Details

- **Port**: 8081
- **Database**: `auth_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
auth-service/src/main/java/com/wydad/digital/
    controller/
    service/
    repository/
    model/
    dto/
    config/
    filter/
    client/
    exception/
    util/
```

## Build & Run

```bash
# Build with tests
mvn -B clean package --file auth-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file auth-service/pom.xml

# Run as part of full stack
docker compose up -d auth-service
```

