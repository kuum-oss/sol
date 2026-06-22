# Система тестирования производительности

Комплексная трёхуровневая система тестирования производительности для микросервисной архитектуры на JVM-стеке.

## Стек технологий

### Целевое приложение (target-app)

| Библиотека | Версия | Назначение |
|---|---|---|
| Spring Boot | 3.4.3 | Основной фреймворк |
| Kotlin | 2.0.0 | Язык реализации |
| Spring MVC | (BOM) | REST API |
| Spring Data JPA | (BOM) | ORM-слой |
| Spring Data Redis (Lettuce) | (BOM) | Distributed-кэш |
| Spring Actuator | (BOM) | Health, метрики |
| Spring WebFlux | (BOM) | WebClient (транзитивная зависимость) |
| Jackson + Kotlin Module | (BOM) | JSON-сериализация |
| kotlinx.serialization | 1.6.3 | JSON-сериализация (альтернатива) |
| Gson | 2.10.1 | JSON-сериализация (альтернатива) |
| OkHttpClient | 4.12.0 | HTTP-клиент |
| Caffeine | 3.1.8 | In-process LRU-кэш |
| HikariCP | (BOM) | Пул соединений PostgreSQL |
| Flyway | (BOM) | Версионирование схемы БД |
| PostgreSQL JDBC | (BOM) | Драйвер БД |
| Resilience4j | 2.2.0 | Circuit Breaker |
| Chaos Monkey for Spring Boot | 3.1.0 | Инъекция сбоев |
| Micrometer + Prometheus | (BOM) | Экспорт метрик |

### Тестирование

| Библиотека | Версия | Назначение |
|---|---|---|
| JMH | 1.37 | Микробенчмарки |
| Gatling | 3.11.3 | Нагрузочное тестирование |
| Toxiproxy Java Client | 2.1.11 | Управление сетевым хаосом из тестов |
| OkHttpClient + MockWebServer | 4.12.0 | HTTP-клиент и мок-сервер в JMH |
| JUnit 5 | (BOM) | Фреймворк для хаос-тестов |

### Инфраструктура (Docker Compose)

| Сервис | Образ | Порт |
|---|---|---|
| PostgreSQL | postgres:15-alpine | 5432 |
| Redis | redis:7-alpine | 6379 |
| Toxiproxy | ghcr.io/shopify/toxiproxy:2.9.0 | 8474, 15432, 16379 |
| InfluxDB | influxdb:2.7-alpine | 8086 |
| Prometheus | prom/prometheus:latest | 9090 |
| Grafana | grafana/grafana:10.2.3 | 3000 |


## Структура проекта

Система организована как мультимодульный проект. Основные компоненты:
- **target-app** — целевое Spring Boot приложение (мишень).
- **performance-tests** — тесты производительности (бенчмарки, нагрузка, хаос).
- **reports** — агрегированные отчеты о тестировании.

Подробная структура папок и файлов описана в разделе [Структура проекта](./ARCHITECTURE.md#структура-проекта) в архитектурной документации.

## Требования

- **JVM 21+**
- **Docker & Docker Compose**

---

## 1. Запуск инфраструктуры

```bash
cd performance-tests/infra
```
```bash
docker-compose up -d
```

| Сервис | Порт | Описание |
|---|---|---|
| PostgreSQL | 5432 | База данных |
| Redis | 6379 | Распределённый кэш |
| Toxiproxy | 8474 | Сетевой хаос |
| InfluxDB | 8086 | Хранение метрик Gatling |
| Prometheus | 9090 | Сбор метрик Actuator |
| Grafana | 3000 | Дашборды (`admin` / `admin`) |

---

## 2. Модуль 1 — Микробенчмарки (JMH)

```bash
./gradlew :performance-tests:benchmarks:runBenchmarks
```

| Бенчмарк | Режим | Что измеряется |
|---|---|---|
| `SerializationBenchmark` | Throughput | Jackson vs Gson vs kotlinx.serialization |
| `CacheBenchmark` | AverageTime | Caffeine: Cache Hit vs Cache Miss |
| `HttpClientBenchmark` | Throughput | OkHttpClient через MockWebServer |
| `BusinessLogicBenchmark` | Throughput | Сортировка и бинарный поиск |

Результаты сохраняются в `reports/jmh_results.json`. При первом запуске создаётся `reports/jmh_baseline.json` как эталон — при последующих запусках деградация **>10%** блокирует сборку.

---

## 3. Модуль 2 — Нагрузочное тестирование (Gatling)

Задача `runLoadTest` сама поднимает `target-app` (через `bootJar`) перед прогоном и останавливает его после — **вручную запускать приложение не нужно**:

```bash
# Smoke — 5 пользователей, быстрая проверка (по умолчанию)
./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=smoke
```

```bash
# Load — рабочая нагрузка 100→500 RPS, 10 минут
./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=load
```

```bash
# Stress — поиск предела: ramp до 2000 RPS за 5 минут
./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=stress
```

```bash
# Soak — долгосрочная стабильность: 200 RPS, 60 минут
./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=soak
```

```bash
# Spike — пиковая нагрузка: 10→1000→10 RPS
./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=spike
```

| Профиль | Нагрузка | p50 | p95 | p99 | Error rate |
|---|---|---|---|---|---|
| `smoke` | 5 VU | <1000ms | <2000ms | <3000ms | <0.1% |
| `load` | 100→500 RPS | <100ms | <300ms | <800ms | <0.1% |
| `stress` | →2000 RPS | <100ms | <300ms | <800ms | <0.1% |
| `soak` | 200 RPS × 60min | <100ms | <300ms | <800ms | <0.1% |
| `spike` | 10→1000→10 RPS | <100ms | <300ms | <800ms | <0.1% |

Отчёты сохраняются в `reports/gatling/`.

> Если всё же нужно запустить `target-app` вручную (например, для просмотра логов в отдельном окне) — не запускайте после этого `runLoadTest` напрямую: задача `startTargetApp` поднимет второй процесс и упадёт из-за занятого порта 8080. Сначала погасите ручной процесс.

---

## 4. Модуль 3 — Chaos Engineering

Убедитесь, что инфраструктура и `target-app` запущены, затем:

```bash
./gradlew :performance-tests:chaos-tests:test
```

| Сценарий | Инструмент | Что проверяется |
|---|---|---|
| **DB Chaos** | Toxiproxy latency | Circuit Breaker на задержках БД |
| **Cache Chaos** | Toxiproxy bandwidth=0 | Fallback при недоступности Redis |
| **Downstream Chaos** | Chaos Monkey latency | Устойчивость при HTTP-задержках |
| **Memory Pressure** | Chaos Monkey memory | Поведение GC при атаке на Heap |
| **Pod Kill** | Actuator shutdown | Graceful shutdown |

> ⚠️ **Важно:** по умолчанию `target-app` подключается к Postgres и Redis напрямую (`localhost:5432`, `localhost:6379`), **минуя** прокси-порты Toxiproxy (`15432`, `16379`). Это значит, что инъекция latency/bandwidth-хаоса через Toxiproxy сама по себе не повлияет на запросы приложения, пока вы не перенаправите `spring.datasource.url` и `spring.data.redis.host/port` в `application.yml` на прокси-порты. Без этой правки тесты DB Chaos / Cache Chaos просто проверяют поведение приложения в обычном режиме (или в mock-режиме, если Toxiproxy не запущен вовсе) — Circuit Breaker и fallback при этом всё равно отрабатывают корректно, но не из-за внесённого хаоса.

---

## Известные ограничения

То, что задокументировано/настроено, но пока не доведено до полностью рабочего состояния:

| Что заявлено | Текущее состояние | Что нужно сделать |
|---|---|---|
| Chaos-тесты через Toxiproxy реально бьют по трафику приложения | `target-app` ходит к Postgres/Redis напрямую, минуя прокси-порты | Перенаправить `application.yml` на порты `15432`/`16379` (см. предупреждение в разделе 4) |
| Gatling пишет метрики в InfluxDB в реальном времени | Нет `gatling.conf` с InfluxDB data writer — Gatling пишет только локальные HTML-отчёты | Добавить `gatling.conf` с `data.writers = [console, file, graphite]` / InfluxDB-плагин и настройками подключения |
| Grafana показывает готовые дашборды | Provisioning настроен, но папка с JSON-дашбордами пуста | Положить экспортированные дашборды (Actuator/Gatling) в `infra/grafana/provisioning/dashboards/` |
| Datasource InfluxDB в Grafana авторизован | `secureJsonData.token` = пароль админа, не настоящий API-токен InfluxDB 2.x | Сгенерировать токен: `docker exec -it influxdb influx auth create --all-access` и подставить в `datasources.yml` |
| Регрессия в JMH блокирует сборку | Проверка работает только при локальном запуске `runBenchmarks`, в CI не интегрирована | Добавить workflow GitHub Actions, вызывающий `runBenchmarks` на PR |
| `BusinessLogicBenchmark` измеряет бизнес-логику приложения | Бенчмарк тестирует обобщённый sort/binary search, не связан с реальным `/api/algo/cpu` (подсчёт простых чисел) | Либо переименовать бенчмарк, либо завести отдельный JMH-бенчмарк, вызывающий `BenchmarkService.computeHeavyTask` |
| Сервис `target-app` поднимается в Docker Compose | Сервис закомментирован в `docker-compose.yml`; Prometheus всё равно скрейпит `target-app:8080` | Либо раскомментировать сервис, либо убрать неактуальный scrape-таргет из `prometheus.yml` |

---

## Архитектурные решения

Система построена на принципах изоляции слоев и воспроизводимости тестов. Основные решения:
- **Трехуровневая модель**: разделение на микробенчмарки (JMH), нагрузочные (Gatling) и хаос-тесты (Toxiproxy).
- **Автоматизация порогов**: сборка блокируется при деградации производительности в JMH.
- **Инфраструктура как код**: полное окружение (Prometheus, Grafana, БД) разворачивается через Docker Compose.
- **Отказоустойчивость**: интеграция Resilience4j для проверки корректности работы Circuit Breaker в условиях сбоев.

Подробное описание библиотек и устройства модулей:
**[ARCHITECTURE.md](./ARCHITECTURE.md)**