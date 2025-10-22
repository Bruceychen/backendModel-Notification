package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/brucechen/notification-service/internal/cache"
	"github.com/brucechen/notification-service/internal/config"
	"github.com/brucechen/notification-service/internal/handler"
	"github.com/brucechen/notification-service/internal/mq"
	"github.com/brucechen/notification-service/internal/repository"
	"github.com/brucechen/notification-service/internal/service"
	"github.com/gin-gonic/gin"
)

func main() {
	// Load configuration
	cfg, err := config.Load("config.yaml")
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize components
	log.Println("Initializing application components...")

	// Initialize repository
	repo, err := repository.NewNotificationRepository(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize repository: %v", err)
	}
	defer func() {
		if err := repo.Close(); err != nil {
			log.Printf("Failed to close repository: %v", err)
		}
	}()
	log.Println("Repository initialized successfully")

	// Initialize Redis cache
	redisCache, err := cache.NewRedisCache(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize Redis cache: %v", err)
	}
	defer func() {
		if err := redisCache.Close(); err != nil {
			log.Printf("Failed to close Redis cache: %v", err)
		}
	}()
	log.Println("Redis cache initialized successfully")

	// Initialize RocketMQ producer
	producer, err := mq.NewNotificationProducer(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize RocketMQ producer: %v", err)
	}
	defer func() {
		if err := producer.Close(); err != nil {
			log.Printf("Failed to close RocketMQ producer: %v", err)
		}
	}()
	log.Println("RocketMQ producer initialized successfully")

	// Initialize RocketMQ consumer
	consumer, err := mq.NewNotificationConsumer(cfg)
	if err != nil {
		log.Fatalf("Failed to initialize RocketMQ consumer: %v", err)
	}
	defer func() {
		if err := consumer.Close(); err != nil {
			log.Printf("Failed to close RocketMQ consumer: %v", err)
		}
	}()
	log.Println("RocketMQ consumer initialized successfully")

	// Initialize service
	notificationService := service.NewNotificationService(repo, redisCache, producer)
	log.Println("Service layer initialized successfully")

	// Initialize handler
	notificationHandler := handler.NewNotificationHandler(notificationService)
	log.Println("Handler layer initialized successfully")

	// Setup Gin router
	gin.SetMode(gin.ReleaseMode) // Use release mode for production
	router := gin.Default()

	// Register routes
	notificationHandler.RegisterRoutes(router)

	// Health check endpoint
	router.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status": "healthy",
			"time":   time.Now().Format(time.RFC3339),
		})
	})

	// Create HTTP server
	srv := &http.Server{
		Addr:    fmt.Sprintf(":%d", cfg.Server.Port),
		Handler: router,
	}

	// Start server in a goroutine
	go func() {
		log.Printf("Starting HTTP server on port %d...", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Failed to start server: %v", err)
		}
	}()

	log.Printf("Notification Service started successfully on port %d", cfg.Server.Port)

	// Wait for interrupt signal to gracefully shutdown the server
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")

	// Graceful shutdown with 5 second timeout
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("Server forced to shutdown: %v", err)
	}

	log.Println("Server exited")
}