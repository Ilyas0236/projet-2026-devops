# payment-service

Processes payments, maintains the transaction ledger, generates PDF invoices, and provides payment history. Used internally by shop-service and ticket-service for transaction finalization.

## Technical Details

- **Port**: 8083
- **Database**: `payment_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
payment-service/src/main/java/com/wydad/digital/
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
mvn -B clean package --file payment-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file payment-service/pom.xml

# Run as part of full stack
docker compose up -d payment-service
```

