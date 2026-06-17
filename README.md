# Система тестирования производительности

Проект содержит комплексную трехуровневую систему тестирования производительности для микросервисной архитектуры на JVM-стеке.

## Структура проекта
```
├── target-app/          # Целевое Spring Boot приложение для тестов
├── performance-tests/
│   ├── benchmarks/      # JMH микробенчмарки (Сериализация, Кэш, HTTP, Бизнес-логика)
│   ├── load-tests/      # Сценарии Gatling с поддержкой профилей (Smoke, Load, Stress, Soak, Spike)
│   ├── chaos-tests/     # Автоматизированные хаос-тесты (Toxiproxy и Chaos Monkey)
│   └── infra/           # Инфраструктура (Docker Compose, Prometheus, Grafana, InfluxDB)
```

## Требования
* JVM 21+ (Сборка настроена на компиляцию под Java 21)
* Docker & Docker Compose

---

## 1. Запуск инфраструктуры (Infra)
В каталоге `performance-tests/infra` выполните:
```bash
docker-compose up -d
```
Это запустит:
* **PostgreSQL** (порт 5432) — база данных
* **Redis** (порт 6379) — распределенный кэш
* **Toxiproxy** (порт 8474) — сетевой хаос
* **InfluxDB** (порт 8086) — хранение метрик Gatling
* **Prometheus** (порт 9090) — сбор метрик Actuator
* **Grafana** (порт 3000) — дашборды (логин/пароль: `admin`/`admin`)

---

## 2. Модуль 1 — Микробенчмарки (JMH)
Запуск JMH тестов (включает сравнение результатов с baseline и блокировку сборки при регрессии > 10%):
```bash
./gradlew :performance-tests:benchmarks:runBenchmarks
```
Результаты сохраняются в `reports/jmh_results.json`. При первом запуске они копируются в `reports/jmh_baseline.json` в качестве эталона.

---

## 3. Модуль 2 — Нагрузочное тестирование (Gatling)
1. Запустите целевое приложение:
   ```bash
   ./gradlew :target-app:bootRun
   ```
2. Запустите сценарии Gatling:
   ```bash
   # По умолчанию запускается профиль Smoke
   ./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=smoke

   # Другие профили: load, stress, soak, spike
   ./gradlew :performance-tests:load-tests:runLoadTest -Dprofile=load
   ```
Отчеты сохраняются в каталоге `reports/gatling/`.

---

## 4. Модуль 3 — Chaos Engineering
Хаос-тесты используют Toxiproxy для симуляции сетевого хаоса и Chaos Monkey для создания сбоев в приложении.

1. Убедитесь, что инфраструктура (`docker-compose`) и `target-app` запущены.
2. Выполните тесты:
   ```bash
   ./gradlew :performance-tests:chaos-tests:test
   ```
Сценарии проверяют:
* **DB Chaos** — задержка сети на БД и срабатывание Resilience4j Circuit Breaker.
* **Cache Chaos** — блокировка Redis и автоматический fallback.
* **Downstream Chaos** — задержки на HTTP-запросах.
* **Memory Pressure** — поведение GC при атаках на Heap.
* **Pod Kill** — Graceful shutdown приложения.
