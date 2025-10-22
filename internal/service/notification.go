package service

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/brucechen/notification-service/internal/cache"
	"github.com/brucechen/notification-service/internal/domain"
	"github.com/brucechen/notification-service/internal/dto"
	"github.com/brucechen/notification-service/internal/mq"
	"github.com/brucechen/notification-service/internal/repository"
)

// NotificationService defines the interface for notification business logic
type NotificationService interface {
	CreateNotification(ctx context.Context, req *dto.NotificationRequest) (*domain.Notification, error)
	GetNotificationByID(ctx context.Context, id int64) (*domain.Notification, error)
	GetRecentNotifications(ctx context.Context) ([]*domain.Notification, error)
	UpdateNotification(ctx context.Context, id int64, req *dto.UpdateNotificationRequest) (*domain.Notification, error)
	DeleteNotification(ctx context.Context, id int64) (bool, error)
}

// NotificationServiceImpl implements NotificationService
type NotificationServiceImpl struct {
	repo     *repository.NotificationRepository
	cache    *cache.RedisCache
	producer *mq.NotificationProducer
}

// NewNotificationService creates a new NotificationService
func NewNotificationService(
	repo *repository.NotificationRepository,
	cache *cache.RedisCache,
	producer *mq.NotificationProducer,
) NotificationService {
	return &NotificationServiceImpl{
		repo:     repo,
		cache:    cache,
		producer: producer,
	}
}

// CreateNotification creates a new notification
func (s *NotificationServiceImpl) CreateNotification(ctx context.Context, req *dto.NotificationRequest) (*domain.Notification, error) {
	// Create notification entity
	notification := &domain.Notification{
		Type:      req.Type,
		Recipient: req.Recipient,
		Subject:   req.Subject,
		Content:   req.Content,
	}

	// Start transaction
	txRepo, err := s.repo.BeginTx(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}

	// Save to database
	if err := txRepo.Save(ctx, notification); err != nil {
		txRepo.Rollback()
		return nil, fmt.Errorf("failed to save notification: %w", err)
	}

	// Commit transaction
	if err := txRepo.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	// After successful commit, perform non-transactional operations
	// These operations are done asynchronously to ensure they don't block the response
	go func() {
		bgCtx := context.Background()

		// Send to message queue
		message := dto.NewNotificationMessage(notification, domain.MessageTypeCreate)
		if err := s.producer.SendNotification(bgCtx, message); err != nil {
			log.Printf("Failed to send notification message: %v", err)
		}

		// Cache the notification
		if err := s.cache.CacheNotification(bgCtx, notification); err != nil {
			log.Printf("Failed to cache notification: %v", err)
		}

		// Clear recent list cache to force rebuild on next read
		if err := s.cache.ClearRecentList(bgCtx); err != nil {
			log.Printf("Failed to clear recent list: %v", err)
		}
	}()

	return notification, nil
}

// GetNotificationByID retrieves a notification by ID (cache-first strategy)
func (s *NotificationServiceImpl) GetNotificationByID(ctx context.Context, id int64) (*domain.Notification, error) {
	// Check cache first
	cachedNotification, err := s.cache.FindNotificationByID(ctx, id)
	if err != nil {
		log.Printf("Failed to get notification from cache: %v", err)
		// Continue to database on cache error
	}
	if cachedNotification != nil {
		return cachedNotification, nil
	}

	// Cache miss - fetch from database
	notification, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to find notification: %w", err)
	}
	if notification == nil {
		return nil, nil // Not found
	}

	// Update cache asynchronously
	go func() {
		bgCtx := context.Background()
		if err := s.cache.CacheNotification(bgCtx, notification); err != nil {
			log.Printf("Failed to cache notification: %v", err)
		}
	}()

	return notification, nil
}

// GetRecentNotifications retrieves the 10 most recent notifications with DCL pattern
func (s *NotificationServiceImpl) GetRecentNotifications(ctx context.Context) ([]*domain.Notification, error) {
	// Try to fetch from Redis cache first
	recentNotifications, err := s.cache.FindRecentNotifications(ctx)
	if err != nil {
		log.Printf("Failed to get recent notifications from cache: %v", err)
		// Continue to database on cache error
	}
	if len(recentNotifications) > 0 {
		return recentNotifications, nil
	}

	// Cache miss - acquire distributed lock to prevent thundering herd
	lockKey := s.cache.GetLockKey("getRecentNotifications")
	acquired, err := s.cache.SetNXWithExpiration(ctx, lockKey, "locked", 30*time.Second)
	if err != nil {
		log.Printf("Failed to acquire lock: %v", err)
		// Fall back to querying database without lock
		return s.repo.FindTop10ByOrderByCreatedAtDesc(ctx)
	}

	if acquired {
		// Lock acquired - we are responsible for rebuilding the cache
		defer func() {
			if err := s.cache.DeleteKey(ctx, lockKey); err != nil {
				log.Printf("Failed to release lock: %v", err)
			}
		}()

		// Double-check cache (DCL pattern) in case it was populated while waiting
		recentNotifications, err := s.cache.FindRecentNotifications(ctx)
		if err != nil {
			log.Printf("Failed to get recent notifications from cache: %v", err)
		}
		if len(recentNotifications) > 0 {
			return recentNotifications, nil
		}

		// Fetch from database
		notifications, err := s.repo.FindTop10ByOrderByCreatedAtDesc(ctx)
		if err != nil {
			return nil, fmt.Errorf("failed to fetch recent notifications: %w", err)
		}

		// Populate cache
		if len(notifications) > 0 {
			if err := s.cache.PopulateRecentList(ctx, notifications); err != nil {
				log.Printf("Failed to populate recent list cache: %v", err)
			}
		}

		return notifications, nil
	}

	// Lock not acquired - another thread is rebuilding cache
	// Wait briefly and retry
	time.Sleep(100 * time.Millisecond)

	// Retry fetching from cache
	recentNotifications, err = s.cache.FindRecentNotifications(ctx)
	if err != nil {
		log.Printf("Failed to get recent notifications from cache after retry: %v", err)
	}
	if len(recentNotifications) > 0 {
		return recentNotifications, nil
	}

	// If cache is still empty, fall back to database
	return s.repo.FindTop10ByOrderByCreatedAtDesc(ctx)
}

// UpdateNotification updates an existing notification with pessimistic locking
func (s *NotificationServiceImpl) UpdateNotification(ctx context.Context, id int64, req *dto.UpdateNotificationRequest) (*domain.Notification, error) {
	// Start transaction
	txRepo, err := s.repo.BeginTx(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to begin transaction: %w", err)
	}

	// Fetch and lock the notification (pessimistic locking)
	notification, err := txRepo.FindNotificationAndLockByID(ctx, id)
	if err != nil {
		txRepo.Rollback()
		return nil, fmt.Errorf("failed to find and lock notification: %w", err)
	}
	if notification == nil {
		txRepo.Rollback()
		return nil, nil // Not found
	}

	// Update fields
	notification.Subject = req.Subject
	notification.Content = req.Content

	// Save changes
	if err := txRepo.Save(ctx, notification); err != nil {
		txRepo.Rollback()
		return nil, fmt.Errorf("failed to update notification: %w", err)
	}

	// Commit transaction
	if err := txRepo.Commit(); err != nil {
		return nil, fmt.Errorf("failed to commit transaction: %w", err)
	}

	// After successful commit, perform cache and MQ operations
	go func() {
		bgCtx := context.Background()

		// Clear recent list cache
		if err := s.cache.ClearRecentList(bgCtx); err != nil {
			log.Printf("Failed to clear recent list: %v", err)
		}

		// Delete single notification cache to prevent stale data
		if err := s.cache.DeleteNotification(bgCtx, id); err != nil {
			log.Printf("Failed to delete notification cache: %v", err)
		}

		// Send update message to MQ
		message := dto.NewNotificationMessage(notification, domain.MessageTypeUpdate)
		if err := s.producer.SendNotification(bgCtx, message); err != nil {
			log.Printf("Failed to send update message: %v", err)
		}
	}()

	return notification, nil
}

// DeleteNotification deletes a notification by ID with pessimistic locking
func (s *NotificationServiceImpl) DeleteNotification(ctx context.Context, id int64) (bool, error) {
	// Start transaction
	txRepo, err := s.repo.BeginTx(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to begin transaction: %w", err)
	}

	// Fetch and lock the notification (pessimistic locking)
	notification, err := txRepo.FindNotificationAndLockByID(ctx, id)
	if err != nil {
		txRepo.Rollback()
		return false, fmt.Errorf("failed to find and lock notification: %w", err)
	}
	if notification == nil {
		txRepo.Rollback()
		return false, nil // Not found
	}

	// Delete the notification
	if err := txRepo.DeleteByID(ctx, id); err != nil {
		txRepo.Rollback()
		return false, fmt.Errorf("failed to delete notification: %w", err)
	}

	// Commit transaction
	if err := txRepo.Commit(); err != nil {
		return false, fmt.Errorf("failed to commit transaction: %w", err)
	}

	// After successful commit, perform cache and MQ operations
	go func() {
		bgCtx := context.Background()

		// Clear recent list cache
		if err := s.cache.ClearRecentList(bgCtx); err != nil {
			log.Printf("Failed to clear recent list: %v", err)
		}

		// Delete single notification cache
		if err := s.cache.DeleteNotification(bgCtx, id); err != nil {
			log.Printf("Failed to delete notification cache: %v", err)
		}

		// Send delete message to MQ
		message := dto.NewNotificationMessage(notification, domain.MessageTypeDelete)
		if err := s.producer.SendNotification(bgCtx, message); err != nil {
			log.Printf("Failed to send delete message: %v", err)
		}
	}()

	return true, nil
}