.PHONY: help build run test clean docker-build docker-run deps tidy

help: ## Display this help message
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-15s %s\n", $$1, $$2}'

deps: ## Download Go dependencies
	go mod download

tidy: ## Tidy Go dependencies
	go mod tidy

build: ## Build the Go application
	go build -o bin/notification-service ./cmd/server

run: ## Run the Go application locally
	go run ./cmd/server/main.go

test: ## Run tests
	go test -v -race -coverprofile=coverage.out ./...

coverage: test ## Generate test coverage report
	go tool cover -html=coverage.out -o coverage.html

clean: ## Clean build artifacts
	rm -rf bin/
	rm -f coverage.out coverage.html

docker-build: ## Build Docker image for Go service
	docker build -f Dockerfile.go -t notification-service-go:latest .

docker-run: ## Run Go service in Docker
	docker run -p 8090:8090 --network host notification-service-go:latest

fmt: ## Format Go code
	go fmt ./...

vet: ## Run Go vet
	go vet ./...

lint: ## Run golangci-lint (requires golangci-lint to be installed)
	golangci-lint run

.DEFAULT_GOAL := help