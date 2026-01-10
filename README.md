# back-end

Reactive Spring Boot WebFlux services:

- product-service (8081) - catalog + SSE
- order-service (8082) - cart + orders
- payment-service (8083) - payments + webhook
- notification-service (8084) - SSE notifications
- bank-mock-service (8085) - mock bank API
- admin-service (8086) - metrics + transactions

Swagger UI is available inside each service at `/swagger-ui` and OpenAPI at `/v3/api-docs`.

Run everything with Docker Compose from the repo root:

```
docker compose up --build
```
