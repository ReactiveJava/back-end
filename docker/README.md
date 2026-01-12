# Настройка Docker

Этот README описывает, как запустить полный проект Reactive Java с Docker Compose, какие сервисы доступны (включая Grafana), а также как запускать тесты и нагрузочные тесты.

## Файлы Compose
- `docker-compose.yml` - полный стек: базы данных, кэш, все backend-сервисы, фронтенд, Prometheus, Grafana и ELK.
- `docker-compose.test.yml` - минимальный стек для интеграционных/e2e тестов (без стека наблюдаемости).
- `docker-compose.load.yml` - оверлей для нагрузочных тестов (k6 + настройка bank-mock). Используется вместе с `docker-compose.yml` (полный стек) или `docker-compose.test.yml` (минимальный стек).

## Требования
- Docker Engine / Docker Desktop с Compose v2.
- Для тестов бэкенда: JDK 21 (Gradle Wrapper включен).

## Запуск полного стека
Из корня репозитория:

```bash
docker compose up --build
```

Полезные варианты:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f order-service
```

Остановить все:

```bash
docker compose down
```

Сбросить все тома данных (Postgres, Grafana, Prometheus, Elasticsearch):

```bash
docker compose down -v
```

## Сервисы и URL

| Сервис | Назначение | URL/Порт |
| --- | --- | --- |
| frontend | React интерфейс | http://localhost:3000 |
| product-service | каталог + SSE обновления | http://localhost:8081 |
| order-service | корзина + заказы + SSE | http://localhost:8082 |
| payment-service | платежи + webhook | http://localhost:8083 |
| notification-service | SSE уведомления | http://localhost:8084 |
| bank-mock-service | мок банковского API | http://localhost:8085 |
| admin-service | метрики + транзакции + SSE | http://localhost:8086 |
| postgres | базы данных | localhost:5432 |
| redis | кэш | localhost:6379 |
| prometheus | хранилище метрик | http://localhost:9090 |
| grafana | дашборды (admin/admin) | http://localhost:3001 |
| kibana | интерфейс логов | http://localhost:5601 |
| elasticsearch | хранилище логов | http://localhost:9200 |
| logstash | прием логов | localhost:5044 |

Каждый backend-сервис публикует:
- Swagger UI: `http://localhost:<port>/swagger-ui`
- OpenAPI: `http://localhost:<port>/v3/api-docs`
- Health: `http://localhost:<port>/actuator/health`
- Метрики Prometheus: `http://localhost:<port>/actuator/prometheus`

## Функциональность (основные API)

Каталог товаров (product-service):
- `GET /api/products` (поддерживает `query`, `category`, `minPrice`, `maxPrice`, `page`, `size`)
- `GET /api/products/{id}`
- `GET /api/products/stream` (SSE)

Корзина и заказы (order-service):
- `GET /api/cart/{userId}`
- `POST /api/cart/{userId}/items`
- `PATCH /api/cart/{userId}/items/{productId}`
- `DELETE /api/cart/{userId}/items/{productId}`
- `DELETE /api/cart/{userId}`
- `POST /api/orders`
- `GET /api/orders?userId=...`
- `GET /api/orders/{id}`
- `GET /api/cart/stream/{userId}` (SSE)

Платежи (payment-service):
- `POST /api/payments`
- `GET /api/payments/{id}`
- `POST /api/payments/webhook` (внутренний callback от bank-mock)

Уведомления (notification-service):
- `GET /api/notifications/stream/{userId}` (SSE)

Админ (admin-service):
- `GET /api/admin/transactions` (поддерживает `orderId`, `status`)
- `GET /api/admin/transactions/stream` (SSE)
- `GET /api/admin/metrics/stream` (SSE)

Фронтенд:
- Откройте `http://localhost:3000` для интерфейса магазина.
- Админский интерфейс доступен по `http://localhost:3000/admin`.

## Наблюдаемость

Метрики:
- Prometheus собирает метрики каждого сервиса по `/actuator/prometheus`.
- Grafana предварительно настроена с Prometheus и папкой дашбордов "Reactive Shop".
- Логин Grafana: `admin` / `admin`.

Логи (ELK):
- Logstash слушает TCP 5044 и отправляет в Elasticsearch индекс `reactive-shop-YYYY.MM.dd`.
- Kibana доступна по http://localhost:5601.

## Тесты

Юнит- и интеграционные тесты бэкенда (включая e2e на Testcontainers):

```bash
cd back-end
./gradlew test
```

Примечания:
- Тест `EndToEndFlowTest` в `order-service` использует `docker-compose.test.yml` через Testcontainers.
- Для этих тестов Docker должен быть запущен.

Если нужно поднять минимальный тестовый стек вручную:

```bash
docker compose -f docker-compose.test.yml up --build
```

## Нагрузочные тесты (k6)

Запуск e2e-сценария нагрузки (минимальный стек):

```bash
docker compose -f docker-compose.test.yml -f docker-compose.load.yml up --build --abort-on-container-exit
```

Для полного стека с наблюдаемостью используйте `docker-compose.yml` вместо `docker-compose.test.yml`.

Настройка нагрузки через переменные окружения:

```bash
VUS=20 DURATION=2m STARTUP_DELAY=20 docker compose -f docker-compose.test.yml -f docker-compose.load.yml up --build --abort-on-container-exit
```

Дополнительные переменные окружения k6, поддерживаемые `load-tests/e2e-load.js`:
- `ORDER_STATUS_TIMEOUT_MS` (по умолчанию 2000)
- `ORDER_STATUS_POLL_MS` (по умолчанию 100)
- `FRONTEND_EVERY` (по умолчанию 1, загрузка UI каждые N итераций)
- `STARTUP_DELAY` (по умолчанию 20, задержка старта k6 до готовности сервисов)

Оверлей нагрузки также устанавливает для bank-mock 0% ошибок и задержку 0 мс для стабильных результатов.

## Настройка bank-mock

Банковский симулятор поддерживает:
- `BANK_FAILURE_RATE` (по умолчанию 0.05)
- `BANK_MIN_DELAY_MS` (по умолчанию 500)
- `BANK_MAX_DELAY_MS` (по умолчанию 3000)

При необходимости переопределяйте в `docker-compose.yml` или через переменные окружения.
