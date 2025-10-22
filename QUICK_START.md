# Quick Start Guide - Go Notification Service

## TL;DR

```bash
# 1. Start infrastructure
cd envSetup && docker-compose up -d && cd ..

# 2. Run the Go service
go run ./cmd/server/main.go

# 3. Test it
curl -X POST http://localhost:8090/notifications \
  -H "Content-Type: application/json" \
  -d '{"type":"EMAIL","recipient":"test@example.com","subject":"Test","content":"Hello!"}'
```

## Prerequisites

- Go 1.21+
- Docker & Docker Compose
- Make (optional, for convenience)

## Installation

### 1. Start Infrastructure Services

```bash
cd envSetup
docker-compose up -d
cd ..
```

This starts:
- MySQL (port 3306)
- Redis (port 6379)
- RocketMQ NameServer (port 9876)
- RocketMQ Broker (ports 10911, 10909)
- RocketMQ Console (http://localhost:8088)

### 2. Install Dependencies

```bash
go mod download
# or
make deps
```

### 3. Run the Service

```bash
# Option A: Direct run (development)
go run ./cmd/server/main.go

# Option B: Build then run
make build
./bin/notification-service

# Option C: Using Make
make run
```

The service starts on **http://localhost:8090**

## Common Commands

### Development
```bash
make help          # Show all available commands
make build         # Build binary
make run           # Run locally
make test          # Run tests
make clean         # Clean build artifacts
```

### Code Quality
```bash
make fmt           # Format code
make vet           # Run go vet
make lint          # Run linter (requires golangci-lint)
```

### Docker
```bash
make docker-build  # Build Docker image
make docker-run    # Run in Docker
```

## API Examples

### Health Check
```bash
curl http://localhost:8090/health
```

### Create Notification
```bash
curl -X POST http://localhost:8090/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "type": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Welcome",
    "content": "Welcome to our service!"
  }'
```

Response:
```json
{
  "id": 1,
  "type": "EMAIL",
  "recipient": "user@example.com",
  "subject": "Welcome",
  "content": "Welcome to our service!",
  "created_at": "2025-10-21T15:30:00Z"
}
```

### Get Notification by ID
```bash
curl http://localhost:8090/notifications/1
```

### Get Recent Notifications (Last 10)
```bash
curl http://localhost:8090/notifications/recent
```

### Update Notification
```bash
curl -X PUT http://localhost:8090/notifications/1 \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "Updated Subject",
    "content": "Updated content"
  }'
```

### Delete Notification
```bash
curl -X DELETE http://localhost:8090/notifications/1
```

Response: 204 No Content

## Configuration

Edit `config.yaml` to customize:

```yaml
server:
  port: 8090        # HTTP server port

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

app:
  notification_topic: notification-topic
  recent_list_key: "recent_notifications"
  notification_key_prefix: "notification:"
  cache_ttl_minutes: 10
```

## Troubleshooting

### Service won't start

**Check if port 8090 is available:**
```bash
lsof -i :8090
# Kill any process using the port if needed
```

**Check if infrastructure is running:**
```bash
docker ps
# Should see MySQL, Redis, RocketMQ containers
```

### Database connection error

```bash
# Test MySQL connection
docker exec -it $(docker ps -qf "name=mysql") \
  mysql -utaskuser -ptaskpass taskdb

# If needed, restart MySQL
docker-compose restart mysql
```

### Redis connection error

```bash
# Test Redis connection
docker exec -it $(docker ps -qf "name=redis") redis-cli ping
# Should return: PONG

# If needed, restart Redis
docker-compose restart redis
```

### RocketMQ connection error

```bash
# Check RocketMQ logs
docker logs $(docker ps -qf "name=namesrv")
docker logs $(docker ps -qf "name=broker")

# Restart RocketMQ if needed
docker-compose restart namesrv broker
```

### View logs

```bash
# Application logs are printed to stdout
# For structured logging, consider adding a logger

# Infrastructure logs
docker-compose logs -f mysql
docker-compose logs -f redis
docker-compose logs -f namesrv
docker-compose logs -f broker
```

## Project Structure

```
.
├── cmd/server/main.go           # Entry point
├── internal/
│   ├── handler/                 # HTTP handlers
│   ├── service/                 # Business logic
│   ├── repository/              # Database access
│   ├── cache/                   # Redis operations
│   ├── mq/                      # RocketMQ
│   ├── domain/                  # Entities
│   ├── dto/                     # Data transfer objects
│   └── config/                  # Configuration
├── config.yaml                  # Config file
├── Makefile                     # Build commands
└── README.go.md                 # Full documentation
```

## Running Tests

```bash
# Run all tests
go test ./...

# Run with coverage
go test -cover ./...

# Run specific package tests
go test ./internal/domain -v

# Generate coverage report
make coverage
open coverage.html
```

## Docker Deployment

### Build Image
```bash
docker build -f Dockerfile.go -t notification-service:latest .
```

### Run Container
```bash
docker run -d \
  --name notification-service \
  -p 8090:8090 \
  --network host \
  notification-service:latest
```

### Using Docker Compose

Add to your `docker-compose.yaml`:
```yaml
services:
  notification-service:
    build:
      context: .
      dockerfile: Dockerfile.go
    ports:
      - "8090:8090"
    depends_on:
      - mysql
      - redis
      - namesrv
      - broker
    environment:
      - DATABASE_HOST=mysql
      - REDIS_HOST=redis
      - ROCKETMQ_NAMESERVER=namesrv:9876
```

## Environment Variables (Optional)

Override config values with environment variables:

```bash
export DATABASE_HOST=localhost
export DATABASE_PORT=3306
export DATABASE_USERNAME=taskuser
export DATABASE_PASSWORD=taskpass
export DATABASE_DATABASE=taskdb
export REDIS_HOST=localhost
export REDIS_PORT=6379
export SERVER_PORT=8090

go run ./cmd/server/main.go
```

## Performance Tips

1. **Connection Pooling**: GORM and go-redis handle this automatically
2. **Cache**: Recent notifications are cached for fast access
3. **Async Operations**: Cache/MQ operations run in goroutines
4. **Indexes**: Ensure database has proper indexes on `created_at`

## Next Steps

- Read `README.go.md` for detailed documentation
- Read `MIGRATION_GUIDE.md` for Java comparison
- Add more tests (see `internal/domain/types_test.go` for examples)
- Configure monitoring and observability
- Set up CI/CD pipeline

## Useful Links

- [Gin Documentation](https://gin-gonic.com/docs/)
- [GORM Documentation](https://gorm.io/docs/)
- [go-redis Documentation](https://redis.uptrace.dev/)
- [RocketMQ Go Client](https://github.com/apache/rocketmq-client-go)

## Support

For issues or questions:
1. Check the logs for error messages
2. Verify infrastructure services are running
3. Review `MIGRATION_GUIDE.md` for detailed explanations
4. Check `README.go.md` for comprehensive documentation

## License

Same as the original Java implementation.