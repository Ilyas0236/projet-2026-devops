# shop-service

Full e-commerce module: product catalog, shopping cart, order lifecycle management, promo codes, and stock tracking. Delegates payment processing to payment-service via internal API.

## Technical Details

- **Port**: 8084
- **Database**: `shop_db`
- **Framework**: Spring Boot 3.3.5 / Java 21

## Package Structure

```text
shop-service/src/main/java/com/wydad/digital/
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
mvn -B clean package --file shop-service/pom.xml

# Run standalone (requires PostgreSQL + .env)
mvn spring-boot:run --file shop-service/pom.xml

# Run as part of full stack
docker compose up -d shop-service
```

