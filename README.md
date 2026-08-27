# Rate Limiter

A Spring Boot based rate limiter using Redis to control the number of
requests a client can make within a fixed time window.

## Version 3

Version 3 adds API-key-based rate limiting, Redis-backed counters,
atomic Redis operations using Lua, configurable endpoint limits,
automated tests, metrics, and Docker Compose support.

## Features

-   Redis-based request counting
-   Endpoint-specific rate limits
-   Configurable request limits
-   API-key-based client identification
-   IP-based fallback
-   Rate-limit response headers
-   HTTP 429 responses
-   `Retry-After` header
-   Redis TTL for automatic expiration
-   Atomic Redis operation using Lua
-   Concurrent request testing
-   Automated tests
-   Metrics endpoint
-   Docker support
-   Docker Compose support

## Current Rate Limits

  Endpoint                  Limit       Window
  ----------------- ------------- ------------
  `/api/login`         5 requests   60 seconds
  `/api/products`     30 requests   60 seconds
  `/api/users`        20 requests   60 seconds

## Architecture

``` text
Client
  |
  | HTTP Request
  v
RateLimitFilter
  |
  +-- X-API-Key present --> API Key as clientId
  |
  +-- No API Key --------> Client IP as clientId
  |
  v
RateLimiter
  |
  v
Redis + Lua
  |
  +-- Atomic counter
  +-- TTL / expiration
  |
  v
RateLimitResult
  |
  +-- Allowed  --> HTTP 200
  |
  +-- Rejected --> HTTP 429
```

## How Rate Limiting Works

1.  `RateLimitFilter` receives the request.
2.  It checks for the `X-API-Key` header.
3.  If an API key exists, it becomes the client identity.
4.  Otherwise, the client IP is used.
5.  The request path selects the endpoint-specific configuration.
6.  `RateLimiter` creates a Redis key.
7.  Redis increments the counter through a Lua script.
8.  The first request sets the expiration timer.
9.  The request is allowed while the counter is within the limit.
10. Once the limit is exceeded, the application returns HTTP `429`.

## API Key Identification

Example request header:

``` text
X-API-Key: sameer-123
```

For `/api/products`, the Redis key becomes:

``` text
rate_limit:products:sameer-123
```

Different API keys have independent counters:

``` text
rate_limit:products:sameer-123
rate_limit:products:rahul-456
```

If no API key is provided, the application falls back to the client IP
address.

## Redis

Redis stores the request counter.

Example:

``` text
Key:   rate_limit:products:sameer-123
Value: 5
TTL:   approximately 60 seconds
```

When the TTL expires, Redis automatically removes the key and a new
rate-limit window starts.

## Lua and Atomic Operations

The rate limiter uses a Lua script executed inside Redis.

Conceptually, the script:

``` text
1. INCR the request counter
2. If this is the first request, set the expiration
3. Return the current count
```

Using Lua makes the counter increment and initial expiration an atomic
Redis-side operation. This is important when multiple requests arrive
concurrently.

## Configuration

Main configuration:

``` text
src/main/resources/application.properties
```

Example:

``` properties
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

Tests run directly from Windows, so the test profile uses:

``` properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

This is stored in:

``` text
src/test/resources/application-test.properties
```

The Docker Compose application uses `redis` as the Redis hostname
because `redis` is the Compose service name.

## Response Headers

Allowed requests include headers such as:

``` text
X-RateLimit-Limit: 30
X-RateLimit-Remaining: 29
```

When the limit is exceeded:

``` text
HTTP 429 Too Many Requests
```

The response also includes:

``` text
Retry-After: <seconds>
```

## Metrics

The application provides:

``` text
GET /metrics
```

Example:

``` json
{
  "totalRequests": 10,
  "allowedRequests": 5,
  "rejectedRequests": 5
}
```

The metrics track total, allowed, and rejected requests.

## Automated Testing

The test suite covers:

-   Application startup
-   Requests within the configured limit
-   Requests exceeding the limit
-   Independent API-key limits
-   Redis key expiration

Current result:

``` text
Tests run: 5
Failures: 0
Errors: 0
Skipped: 0
```

Run tests on Windows:

``` powershell
.\mvnw.cmd test
```

Run a clean test:

``` powershell
.\mvnw.cmd clean test
```

## Docker

The project includes a `Dockerfile` for the Spring Boot application.

Build the application JAR:

``` powershell
.\mvnw.cmd clean package
```

The generated JAR is:

``` text
target/demo-0.0.1-SNAPSHOT.jar
```

## Docker Compose

Docker Compose runs the Spring Boot application and Redis together.

``` text
Docker Compose
|
+-- rate-limiter-app
|      |
|      +-- Spring Boot :8081
|              |
|              v
|             Redis
|
+-- rate-limiter-redis
       |
       +-- Redis :6379
```

Start the environment:

``` powershell
docker compose up --build
```

Application:

``` text
http://localhost:8081
```

Redis:

``` text
localhost:6379
```

Check containers:

``` powershell
docker ps
```

Test Redis:

``` powershell
docker exec -it rate-limiter-redis redis-cli ping
```

Expected:

``` text
PONG
```

## API Examples

### Products

``` text
GET http://localhost:8081/api/products
X-API-Key: sameer-123
```

Configured limit:

``` text
30 requests / 60 seconds
```

### Login

``` text
GET http://localhost:8081/api/login
```

Configured limit:

``` text
5 requests / 60 seconds
```

### Users

``` text
GET http://localhost:8081/api/users
```

Configured limit:

``` text
20 requests / 60 seconds
```

### Metrics

``` text
GET http://localhost:8081/metrics
```

## Example Rate-Limit Behavior

For `/api/products`:

``` text
Limit = 30 requests / 60 seconds

Request 1  -> 200 -> Remaining: 29
Request 2  -> 200 -> Remaining: 28
...
Request 30 -> 200 -> Remaining: 0
Request 31 -> 429 -> Too Many Requests
```

After the 60-second window expires, the Redis key is removed and the
counter starts again.

## Project Structure

``` text
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
+-- pom.xml
+-- mvnw
+-- mvnw.cmd
+-- README.md
```

## V2 to V3 Improvements

``` text
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

The main goal of V3 is to make the rate limiter more configurable,
testable, and closer to a production-style implementation.

## Important Design Decisions

### Why Redis?

Redis provides fast in-memory storage and atomic operations that are
useful for request counters.

### Why API keys?

An API key provides a client identity that is more specific than an IP
address. Different API keys can therefore maintain independent counters.

### Why IP fallback?

If a client does not provide an API key, the application can still apply
rate limiting using the client's IP address.

### Why Lua?

Lua allows the Redis counter increment and initial expiration to execute
atomically inside Redis.

### Why Docker Compose?

Docker Compose makes it easier to run the Spring Boot application and
Redis together with consistent networking and configuration.

## Future Improvements

Possible future improvements include:

-   Proper API-key authentication and validation
-   API-key storage in a database
-   Different limits based on user roles
-   Distributed rate limiting across multiple application instances
-   Prometheus/Grafana monitoring
-   Sliding-window rate limiting
-   Token-bucket algorithm
-   More comprehensive load testing
-   More detailed metrics by endpoint and client
-   Authentication and authorization integration

## Version Status

``` text
Rate Limiter V3

Core implementation       COMPLETE
Redis integration         COMPLETE
API-key support           COMPLETE
Lua atomic operation      COMPLETE
Automated tests           COMPLETE
Docker support            COMPLETE
Docker Compose            COMPLETE
Metrics                   COMPLETE
Documentation             COMPLETE
```

## Next Step

The next stage is a complete code review and interview preparation. The
review will focus on understanding the implementation, design decisions,
trade-offs, concurrency, Redis, Lua, Docker, and possible improvements.
