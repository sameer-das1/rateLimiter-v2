# Rate Limiter

A Spring Boot based rate limiter using Redis to control the number of
requests a client can make within a fixed time window.

## Version 4

Version 4 extends the rate limiter into a horizontally scaled system.

It runs multiple Spring Boot application instances behind an Nginx load
balancer while using a shared Redis instance for rate-limit state.

This allows multiple application instances to share the same request
counter and continue serving requests even if one application instance
goes down.

## Features

- Redis-based request counting
- Endpoint-specific rate limits
- Configurable request limits
- API-key-based client identification
- IP-based fallback
- Rate-limit response headers
- HTTP 429 responses
- `Retry-After` header
- Redis TTL for automatic expiration
- Atomic Redis operation using Lua
- Concurrent request testing
- Automated tests
- Metrics endpoint
- Docker support
- Docker Compose support
- Horizontal scaling
- Multiple Spring Boot application instances
- Nginx load balancer
- Shared Redis state
- Application instance failure handling

## Current Rate Limits

| Endpoint | Limit | Window |
|---|---:|---:|
| `/api/login` | 5 requests | 60 seconds |
| `/api/products` | 30 requests | 60 seconds |
| `/api/users` | 20 requests | 60 seconds |

## Architecture

```text
                         Client
                           |
                           v
                    +--------------+
                    |    Nginx     |
                    | Load Balancer|
                    |    :8080     |
                    +------+-------+
                           |
                  +--------+--------+
                  |                 |
                  v                 v
           +-------------+   +-------------+
           |    App 1    |   |    App 2    |
           | Spring Boot |   | Spring Boot |
           |    :8081    |   |    :8081    |
           +------+------+   +------+------+
                  |                 |
                  +--------+--------+
                           |
                           v
                    +--------------+
                    |    Redis     |
                    |    :6379     |
                    +--------------+
```

## How Rate Limiting Works

1. Nginx receives the client request.
2. Nginx forwards the request to one of the Spring Boot application instances.
3. `RateLimitFilter` receives the request.
4. It checks for the `X-API-Key` header.
5. If an API key exists, it becomes the client identity.
6. Otherwise, the client IP is used.
7. The request path selects the endpoint-specific configuration.
8. `RateLimiter` creates a Redis key.
9. Redis increments the counter through a Lua script.
10. The first request sets the expiration timer.
11. The request is allowed while the counter is within the limit.
12. Once the limit is exceeded, the application returns HTTP `429`.

## Redis

Redis stores the request counter and is shared by all application instances.

Example:

```text
Key:   rate_limit:products:sameer-123
Value: 5
TTL:   approximately 60 seconds
```

When the TTL expires, Redis automatically removes the key and a new
rate-limit window starts.

## Lua and Atomic Operations

The rate limiter uses a Lua script executed inside Redis.

Conceptually:

```text
1. INCR the request counter
2. If this is the first request, set the expiration
3. Return the current count
```

Using Lua makes the counter increment and initial expiration an atomic
Redis-side operation.

## Horizontal Scaling

Version 4 runs multiple application instances:

```text
                 Client
                   |
                   v
                Nginx
                /   \
               v     v
            App 1   App 2
               \     /
                \   /
                 Redis
```

Both application instances use the same Redis instance, so the
rate-limit counter is shared.

For example, with a limit of 5:

```text
Request 1 -> App 1 -> Redis counter = 1
Request 2 -> App 2 -> Redis counter = 2
Request 3 -> App 1 -> Redis counter = 3
Request 4 -> App 2 -> Redis counter = 4
Request 5 -> App 1 -> Redis counter = 5
Request 6 -> App 2 -> Redis counter = 6 -> HTTP 429
```

## Load Balancer

Nginx is used as the load balancer.

The client communicates with:

```text
http://localhost:8080
```

Nginx forwards requests to:

```text
app1:8081
app2:8081
```

The application instances are not directly exposed to the host.

## Failure Handling

Version 4 was tested by stopping one application instance while keeping
the other instance running.

Example:

```text
App 1 -> DOWN
App 2 -> UP
Nginx -> UP
Redis -> UP
```

Requests sent through Nginx can still be served by the remaining
application instance.

## Configuration

Main configuration:

```text
src/main/resources/application.properties
```

```properties
spring.application.name=demo

server.port=8081

spring.data.redis.host=redis
spring.data.redis.port=6379

rate-limit.login.max-requests=5
rate-limit.login.window-seconds=60

rate-limit.products.max-requests=30
rate-limit.products.window-seconds=60

rate-limit.users.max-requests=20
rate-limit.users.window-seconds=60
```

### Test Configuration

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Stored in:

```text
src/test/resources/application-test.properties
```

## Response Headers

Allowed requests include:

```text
X-RateLimit-Limit: 30
X-RateLimit-Remaining: 29
```

When the limit is exceeded:

```text
HTTP 429 Too Many Requests
Retry-After: <seconds>
```

## Metrics

The application provides:

```text
GET /metrics
```

Example:

```json
{
  "totalRequests": 10,
  "allowedRequests": 5,
  "rejectedRequests": 5
}
```

## Automated Testing

The test suite covers:

- Application startup
- Requests within the configured limit
- Requests exceeding the limit
- Independent API-key limits
- Redis key expiration

Run tests:

```powershell
.\mvnw.cmd test
```

Run a clean test:

```powershell
.\mvnw.cmd clean test
```

Expected successful result:

```text
Failures: 0
Errors: 0
```

## Docker

Build the application JAR:

```powershell
.\mvnw.cmd clean package
```

Generated JAR:

```text
target/demo-0.0.1-SNAPSHOT.jar
```

## Docker Compose

Docker Compose runs:

```text
Docker Compose
|
+-- Nginx :8080
|     |
|     +-- app1:8081
|     +-- app2:8081
|
+-- App 1
|
+-- App 2
|
+-- Redis :6379
```

Start:

```powershell
docker compose up --build -d
```

Check:

```powershell
docker compose ps
```

Expected services:

```text
rate-limiter-app-1
rate-limiter-app-2
rate-limiter-nginx
rate-limiter-redis
```

Stop:

```powershell
docker compose down
```

Test Redis:

```powershell
docker exec -it rate-limiter-redis redis-cli ping
```

Expected:

```text
PONG
```

## API Examples

### Products

```text
GET http://localhost:8080/api/products
X-API-Key: sameer-123
```

Limit:

```text
30 requests / 60 seconds
```

### Login

```text
GET http://localhost:8080/api/login
```

Limit:

```text
5 requests / 60 seconds
```

### Users

```text
GET http://localhost:8080/api/users
```

Limit:

```text
20 requests / 60 seconds
```

### Metrics

```text
GET http://localhost:8080/metrics
```

## Example Rate-Limit Behavior

For `/api/products`:

```text
Limit = 30 requests / 60 seconds

Request 1  -> 200 -> Remaining: 29
Request 2  -> 200 -> Remaining: 28
...
Request 30 -> 200 -> Remaining: 0
Request 31 -> 429 -> Too Many Requests
```

## Project Structure

```text
demo
|
+-- src
|   |
|   +-- main
|   |   |
|   |   +-- java/com/example/demo
|   |   |   |
|   |   |   +-- controller
|   |   |   |   +-- MetricsController.java
|   |   |   |
|   |   |   +-- filter
|   |   |   |   +-- RateLimitFilter.java
|   |   |   |
|   |   |   +-- limiter
|   |   |   |   +-- RateLimiter.java
|   |   |   |   +-- RateLimitResult.java
|   |   |   |
|   |   |   +-- metrics
|   |   |       +-- RateLimitMetrics.java
|   |   |
|   |   +-- resources
|   |       +-- application.properties
|   |
|   +-- test
|       |
|       +-- java/com/example/demo
|       |   +-- RateLimiterIntegrationTest.java
|       |
|       +-- resources
|           +-- application-test.properties
|
+-- Dockerfile
+-- docker-compose.yml
+-- nginx.conf
+-- pom.xml
+-- mvnw
+-- mvnw.cmd
+-- README.md
```

## V2 to V3 Improvements

```text
V2
 |
 +-- Basic Redis rate limiting
 |
 v
V3
 |
 +-- Endpoint-specific configuration
 +-- API-key-based identification
 +-- IP fallback
 +-- Atomic Redis Lua operation
 +-- Concurrency testing
 +-- Automated tests
 +-- Metrics
 +-- Dockerfile
 +-- Docker Compose
```

## V3 to V4 Improvements

```text
V3
 |
 +-- Single Spring Boot application instance
 +-- Redis-backed rate limiting
 +-- Lua atomic operation
 |
 v
V4
 |
 +-- Multiple Spring Boot application instances
 +-- Horizontal scaling
 +-- Nginx load balancer
 +-- Shared Redis state
 +-- Single public entry point
 +-- Application failure testing
 +-- Docker Compose distributed architecture
```

## Important Design Decisions

### Why Redis?

Redis provides fast in-memory storage and atomic operations useful for
request counters and shared state.

### Why API keys?

An API key provides a client identity more specific than an IP address.
Different API keys can maintain independent counters.

### Why IP fallback?

If a client does not provide an API key, the application can still apply
rate limiting using the client IP address.

### Why Lua?

Lua allows the Redis counter increment and initial expiration to execute
atomically inside Redis.

### Why Horizontal Scaling?

Running multiple application instances allows workload distribution and
allows another instance to continue serving requests if one instance
goes down.

### Why a Load Balancer?

The load balancer provides a single entry point for clients and
distributes requests across application instances.

### Why Shared Redis?

Each application instance must see the same rate-limit state. Shared
Redis prevents each instance from maintaining a separate counter.

### Why Docker Compose?

Docker Compose makes it easier to run the complete distributed
environment with consistent networking and configuration.

## Future Improvements

- Proper API-key authentication and validation
- API-key storage in a database
- Different limits based on user roles
- Prometheus/Grafana monitoring
- Sliding-window rate limiting
- Token-bucket algorithm
- More comprehensive load testing
- More detailed metrics by endpoint and client
- Authentication and authorization integration
- Redis replication/high availability
- Redis Cluster
- More advanced load-balancer health checking

## Version Status

```text
Rate Limiter V4

Core implementation             COMPLETE
Redis integration               COMPLETE
API-key support                 COMPLETE
Lua atomic operation            COMPLETE
Automated tests                 COMPLETE
Docker support                  COMPLETE
Docker Compose                  COMPLETE
Metrics                         COMPLETE

Horizontal scaling              COMPLETE
Multiple app instances          COMPLETE
Nginx load balancer             COMPLETE
Shared Redis state              COMPLETE
Failure testing                 COMPLETE

Documentation                   COMPLETE
```

