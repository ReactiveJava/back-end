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

Observability:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin/admin)
- Metrics: http://localhost:8081/actuator/prometheus (repeat for each service port)
- Logging (ELK):
  - Kibana: http://localhost:5601
  - Elasticsearch: http://localhost:9200
  - Logstash TCP input: 5044 (index: reactive-shop-YYYY.MM.dd)

End-to-end load tests (k6):
```
docker compose -f docker-compose.yml -f docker-compose.load.yml up --build --abort-on-container-exit --exit-code-from k6
```

Tune load with env vars:
```
VUS=20 DURATION=2m docker compose -f docker-compose.yml -f docker-compose.load.yml up --build --abort-on-container-exit --exit-code-from k6
```
