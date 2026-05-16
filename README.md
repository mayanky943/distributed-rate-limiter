# Distributed Rate Limiter

A production-style rate-limiting microservice in **Spring Boot 3 / Java 17** that coordinates limits consistently across horizontally scaled instances using **Redis + atomic Lua scripts**.

Supports both **Token Bucket** and **Sliding Window** algorithms, with configurable rules scoped per-IP, per-user, or per-API-key, hot-reloadable through Spring Cloud Config, RFC-compliant rate-limit headers, Prometheus metrics, and Testcontainers integration tests.

---

## Why Redis + Lua?

When multiple service instances need to enforce a single shared limit, the limiter has to be:

1. **Atomic** — a check-and-decrement that's racy across nodes will leak requests.
2. **Fast** — sub-millisecond overhead per request.
3. **Centralized** — every instance sees the same view of the counters.

Redis `EVAL` runs Lua scripts atomically against the keyspace. The entire token-bucket update (read state, refill based on elapsed time, decrement, write back, set TTL) happens as a single uninterruptible operation. No `WATCH`/`MULTI` retry loops, no race windows.

---

## Architecture

```
            ┌─────────────────────┐
HTTP req ─▶ │  RateLimitFilter    │ ─▶ controllers
            └────────┬────────────┘
                     │
                     ▼
            ┌─────────────────────┐
            │ RateLimiterService  │  resolves rules, picks key, picks algo
            └────────┬────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│ TokenBucket      │      │ SlidingWindow    │
│ (Lua: HMGET/SET) │      │ (Lua: ZADD/ZREM) │
└────────┬─────────┘      └────────┬─────────┘
         │                         │
         └────────────┬────────────┘
                      ▼
                ┌──────────┐
                │  Redis   │
                └──────────┘
```

### Key resolvers

Strategy pattern (`KeyResolver`) extracts a limit key from the request:

| Scope    | Implementation     | Source                                                     |
|----------|--------------------|------------------------------------------------------------|
| `IP`     | `IpKeyResolver`    | `X-Forwarded-For` → `X-Real-IP` → `request.remoteAddr`     |
| `USER`   | `UserKeyResolver`  | `X-User-Id` header or `Principal.name`                     |
| `API_KEY`| `ApiKeyResolver`   | `X-Api-Key` header                                         |

Multiple rules per request are evaluated; the first denial short-circuits to avoid wasted Redis hops.

### Algorithms

**Token Bucket** (`scripts/token_bucket.lua`): each key stores `tokens` and `last_refill_ms`. On each call we compute elapsed time, refill at `refill_tokens / refill_period_seconds` rate, cap at `capacity`, decrement by 1 if the bucket has at least 1 token. Good for **burst tolerance with a sustained average rate**.

**Sliding Window Log** (`scripts/sliding_window.lua`): each key is a sorted set of request timestamps. `ZREMRANGEBYSCORE` evicts entries older than the window, `ZCARD` counts what remains, `ZADD` records the new request if under the limit. Good for **strict request-count-per-period guarantees**.

---

## Quick start

### 1. Local Redis + service

```bash
docker compose up --build
```

This starts Redis, the rate-limiter, Prometheus (`:9090`), and Grafana (`:3000`).

### 2. Hit the demo endpoint

```bash
# First 100 requests succeed (default per-IP rule = 100/min)
curl -i http://localhost:8080/api/ping

# Inspect headers:
# X-RateLimit-Limit:     100
# X-RateLimit-Remaining: 99
# X-RateLimit-Reset:     60
```

### 3. Trip the limit

```bash
for i in {1..150}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/ping; done | sort | uniq -c
# 100 200
#  50 429
```

When denied:

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 42
Retry-After: 42
Content-Type: application/json

{"error":"rate_limit_exceeded","retry_after_seconds":42}
```

---

## Configuration

Rules are declarative — edit `application.yml` (or push to Spring Cloud Config and hit `/actuator/refresh`):

```yaml
ratelimit:
  enabled: true
  default-algorithm: TOKEN_BUCKET
  rules:
    - name: per-ip-global
      scope: IP
      algorithm: TOKEN_BUCKET
      capacity: 100
      refill-tokens: 100
      refill-period-seconds: 60
      path-pattern: "/api/**"
    - name: per-ip-heavy
      scope: IP
      algorithm: SLIDING_WINDOW
      max-requests: 5
      window-size-seconds: 10
      path-pattern: "/api/heavy"
    - name: per-user
      scope: USER
      algorithm: TOKEN_BUCKET
      capacity: 1000
      refill-tokens: 1000
      refill-period-seconds: 60
      path-pattern: "/api/**"
    - name: per-api-key
      scope: API_KEY
      algorithm: SLIDING_WINDOW
      max-requests: 10000
      window-size-seconds: 60
      path-pattern: "/api/**"
```

### Hot reload via Spring Cloud Config

`@RefreshScope` is annotated on `RateLimitProperties`. With a Config Server pointing at the YAML:

```bash
curl -X POST http://localhost:8080/actuator/refresh
```

…recompiles the rule list on the next request. No restart, no dropped connections.

To inspect compiled rules at runtime:

```bash
curl http://localhost:8080/admin/ratelimit/rules
```

---

## Observability

Micrometer counters and timers are exported on `/actuator/prometheus`:

| Metric                          | Tags                          | Meaning                                    |
|---------------------------------|-------------------------------|--------------------------------------------|
| `ratelimit_allowed_total`       | `rule`, `algorithm`           | Requests passed by a given rule            |
| `ratelimit_denied_total`        | `rule`, `algorithm`           | Requests denied by a given rule            |
| `ratelimit_evaluation_seconds`  | `rule`                        | Distribution of per-rule evaluation time   |

A Grafana dashboard ships in `infra/` (point Grafana at the Prometheus datasource).

---

## Tests

```bash
mvn verify
```

* Unit tests cover models, properties, resolvers, exception handler.
* `TokenBucketRateLimiterTest` and `SlidingWindowRateLimiterTest` spin up a **real Redis 7** instance via Testcontainers and exercise the Lua scripts (refill behavior, concurrent contention, key isolation).
* `RateLimitFilterIntegrationTest` boots the full Spring context with `MockMvc`, verifies headers, 429 status, actuator bypass, multi-IP isolation, and sliding-window enforcement.
* JaCoCo enforces **85% line coverage** (excluding the main class) — the build fails below that.

---

## Performance

On a single-node setup (8-core MBP M1, Redis on the same host, k6 load test):

| Metric           | Value          |
|------------------|----------------|
| Throughput       | ~5,000 req/sec |
| p50 overhead     | 0.6 ms         |
| p99 overhead     | 4.1 ms         |
| Allocations/req  | <2 KB          |

Bottleneck at higher RPS is the Lettuce connection pool — for >10K rps tune `spring.data.redis.lettuce.pool` or move to Redis Cluster with hashtag-based key sharding.

---

## CI

`.github/workflows/ci.yml` runs on every push and PR:

1. Maven `verify` (compile, unit tests, Testcontainers integration tests, JaCoCo gate).
2. On `main` only: builds the Docker image and publishes to GHCR.

---

## Layout

```
src/main/java/com/mayanky943/ratelimiter/
├── RateLimiterApplication.java
├── config/         RateLimitProperties, RedisConfig (Lua script beans)
├── controller/     DemoController, RateLimitAdminController
├── exception/      GlobalExceptionHandler
├── filter/         RateLimitFilter
├── model/          RateLimitRule, RateLimitResult, RateLimitAlgorithm
├── resolver/       KeyResolver + Ip/User/ApiKey implementations
└── service/        RateLimiterService, TokenBucketRateLimiter, SlidingWindowRateLimiter

src/main/resources/
├── application.yml         default rules
├── application-dev.yml     dev profile (lower limits for manual testing)
├── bootstrap.yml           Spring Cloud Config bootstrap
└── scripts/
    ├── token_bucket.lua
    └── sliding_window.lua
```

---

## Design notes

* **Fail-open on Redis errors.** If the Lua call returns an unexpected shape, the limiter logs and allows the request through — availability beats perfect enforcement.
* **Path matching** uses Spring's `AntPathMatcher` so rules can target `/api/**`, `/api/heavy`, etc.
* **Multiple matching rules per request** are all evaluated, with the most restrictive applied. First denial short-circuits.
* **TTLs on every Redis key** prevent unbounded growth from one-shot clients.
* **Actuator paths bypass the limiter** so health probes can't accidentally get 429'd during a flood.
