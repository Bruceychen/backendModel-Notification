# Notification Service - Go Implementation

This is a Go refactoring of the Spring Boot notification service, implementing the same RESTful API for managing email and SMS notifications with MySQL, Redis, and RocketMQ integration.

## Tech Stack

- **Go 1.21+**
- **Gin** - HTTP web framework
- **GORM** - ORM for MySQL
- **go-redis** - Redis client
- **RocketMQ Client Go** - Message queue integration
- **Viper** - Configuration management

## Project Structure

```
.
├── cmd/
│   └── server/
│       └── main.go              # Application entry point
├── internal/
│   ├── config/
│   │   └── config.go            # Configuration management
│   ├── domain/
│   │   ├── notification.go      # Domain entities
│   │   └── types.go             # Enums and types
│   ├── dto/
│   │   ├── request.go           # Request DTOs
│   │   ├── response.go          # Response DTOs
│   │   └── message.go           # MQ message DTOs
│   ├── repository/
│   │   └── notification.go      # Database access layer (GORM)
│   ├── service/
│   │   └── notification.go      # Business logic
│   ├── handler/
│   │   └── notification.go      # HTTP handlers (Gin)
│   ├── cache/
│   │   └── redis.go             # Redis operations
│   └── mq/
│       ├── producer.go          # RocketMQ producer
│       └── consumer.go          # RocketMQ consumer
├── config.yaml                  # Configuration file
├── Dockerfile.go                # Dockerfile
├── Makefile                     # Build automation
└── go.mod                       # Go module definition
```

## Key Design Patterns

### 1. Repository Pattern
The repository layer abstracts database operations using GORM with:
- Pessimistic locking for UPDATE/DELETE operations (`SELECT ... FOR UPDATE`)
- Optimistic locking via version field
- Transaction management

### 2. Cache-Aside Pattern
- Single notification cache with 10-minute TTL
- Recent notifications stored in Redis ZSET
- Double-Checked Locking (DCL) pattern prevents cache stampede

### 3. Transaction Synchronization
Non-transactional operations (Redis cache updates, RocketMQ sends) are executed in goroutines **after** successful DB commit to ensure consistency.

### 4. Distributed Locking
Redis-based distributed locks prevent thundering herd when rebuilding the recent notifications cache.

## API Endpoints

All endpoints match the Java Spring Boot implementation:

- `POST /notifications` - Create notification
- `GET /notifications/{id}` - Get notification by ID (cache-first)
- `GET /notifications/recent` - Get 10 most recent notifications (cached ZSET)
- `PUT /notifications/{id}` - Update notification (with pessimistic lock)
- `DELETE /notifications/{id}` - Delete notification (with pessimistic lock)
- `GET /health` - Health check endpoint

## Building and Running

### Prerequisites

- Go 1.21 or higher
- Docker and Docker Compose (for infrastructure)
- MySQL 8.0
- Redis 7
- RocketMQ 5.1.4

### Start Infrastructure

```bash
# Start MySQL, Redis, and RocketMQ
docker-compose up -d
```

### Build and Run Locally

```bash
# Download dependencies
make deps

# Build the application
make build

# Run the application
make run

# Or directly with go run
go run ./cmd/server/main.go
```

The service will start on port 8090.

### Using Docker

```bash
# Build Docker image
make docker-build

# Run in Docker
make docker-run
```

### Running Tests

```bash
# Run all tests
make test

# Generate coverage report
make coverage
```

## Configuration

Configuration is managed via `config.yaml`:

```yaml
server:
  port: 8090

database:
  host: localhost
  port: 3306
  username: taskuser
  password: taskpass
  database: taskdb

redis:
  host: localhost
  port: 6379
  db: 0

rocketmq:
  name_servers:
    - "127.0.0.1:9876"
  producer_group: notification_producer_group
  consumer_group: notification_consumer_group
  send_timeout: 10000
  retry_times: 3

app:
  notification_topic: notification-topic
  recent_list_key: "recent_notifications"
  notification_key_prefix: "notification:"
  cache_ttl_minutes: 10
```

## Key Differences from Java Implementation

### Transaction Management
- **Java**: Uses Spring's `@Transactional` with `TransactionSynchronizationManager`
- **Go**: Manual transaction management with GORM's `Begin/Commit/Rollback` + goroutines for post-commit operations

### Pessimistic Locking
- **Java**: `@Lock(LockModeType.PESSIMISTIC_WRITE)`
- **Go**: GORM's `Clauses(clause.Locking{Strength: "UPDATE"})`

### Dependency Injection
- **Java**: Spring's constructor injection with `@RequiredArgsConstructor`
- **Go**: Manual dependency injection in `main.go`

### Error Handling
- **Java**: Exception-based with `@ControllerAdvice`
- **Go**: Explicit error return values with error checking

### Concurrency
- **Java**: Thread-based with synchronized blocks
- **Go**: Goroutines with channels and context for cancellation

## Development Commands

```bash
make help          # Show all available commands
make build         # Build the application
make run           # Run locally
make test          # Run tests
make clean         # Clean build artifacts
make fmt           # Format code
make vet           # Run go vet
make docker-build  # Build Docker image
```

## Testing the API

You can use the same Postman collection from the Java implementation:

```bash
# Health check
curl http://localhost:8090/health

# Create notification
curl -X POST http://localhost:8090/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "EMAIL",
    "recipient": "test@example.com",
    "subject": "Test Notification",
    "content": "This is a test notification from Go service"
  }'

# Get notification by ID
curl http://localhost:8090/notifications/1

# Get recent notifications
curl http://localhost:8090/notifications/recent
```

## Performance Considerations

1. **Connection Pooling**: GORM and go-redis automatically handle connection pooling
2. **Goroutines**: Post-commit operations run asynchronously to avoid blocking requests
3. **Redis Pipeline**: Batch operations use Redis pipelining for efficiency
4. **Distributed Locks**: Prevent cache stampede during high concurrency

## Monitoring and Observability

The application logs important events:
- Component initialization
- Database transactions
- Cache operations
- Message queue operations
- Error conditions

Consider adding:
- Prometheus metrics
- Distributed tracing (OpenTelemetry)
- Structured logging (zap, logrus)

## Migration from Java

This Go implementation maintains **100% API compatibility** with the Java Spring Boot version, allowing seamless migration without client changes.

Key architectural patterns are preserved:
- Transaction synchronization pattern
- Double-checked locking for cache
- Pessimistic locking for updates/deletes
- Cache-aside pattern
- Message queue integration

## License

Same license as the original Java implementation.