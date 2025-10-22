package cache

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/brucechen/notification-service/internal/config"
	"github.com/brucechen/notification-service/internal/domain"
	"github.com/redis/go-redis/v9"
)

// RedisCache handles all Redis caching operations
type RedisCache struct {
	client                *redis.Client
	recentListKey         string
	notificationKeyPrefix string
	cacheTTL              time.Duration
}

// NewRedisCache creates a new RedisCache instance
func NewRedisCache(cfg *config.Config) (*RedisCache, error) {
	client := redis.NewClient(&redis.Options{
		Addr: cfg.Redis.GetRedisAddr(),
		DB:   cfg.Redis.DB,
	})

	// Test connection
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		return nil, fmt.Errorf("failed to connect to Redis: %w", err)
	}

	return &RedisCache{
		client:                client,
		recentListKey:         cfg.App.RecentListKey,
		notificationKeyPrefix: cfg.App.NotificationKeyPrefix,
		cacheTTL:              time.Duration(cfg.App.CacheTTLMinutes) * time.Minute,
	}, nil
}

// FindNotificationByID retrieves a notification from cache by ID
func (r *RedisCache) FindNotificationByID(ctx context.Context, id int64) (*domain.Notification, error) {
	key := fmt.Sprintf("%s%d", r.notificationKeyPrefix, id)

	data, err := r.client.Get(ctx, key).Result()
	if err == redis.Nil {
		return nil, nil // Cache miss
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get notification from cache: %w", err)
	}

	var notification domain.Notification
	if err := json.Unmarshal([]byte(data), &notification); err != nil {
		return nil, fmt.Errorf("failed to unmarshal notification: %w", err)
	}

	return &notification, nil
}

// CacheNotification stores a notification in cache with TTL
func (r *RedisCache) CacheNotification(ctx context.Context, notification *domain.Notification) error {
	key := fmt.Sprintf("%s%d", r.notificationKeyPrefix, notification.ID)

	data, err := json.Marshal(notification)
	if err != nil {
		return fmt.Errorf("failed to marshal notification: %w", err)
	}

	if err := r.client.Set(ctx, key, data, r.cacheTTL).Err(); err != nil {
		return fmt.Errorf("failed to cache notification: %w", err)
	}

	return nil
}

// DeleteNotification removes a notification from cache
func (r *RedisCache) DeleteNotification(ctx context.Context, id int64) error {
	key := fmt.Sprintf("%s%d", r.notificationKeyPrefix, id)
	return r.client.Del(ctx, key).Err()
}

// FindRecentNotifications retrieves the recent notifications list from cache
func (r *RedisCache) FindRecentNotifications(ctx context.Context) ([]*domain.Notification, error) {
	// Get all members from ZSET in descending order (newest first)
	results, err := r.client.ZRevRange(ctx, r.recentListKey, 0, -1).Result()
	if err != nil {
		return nil, fmt.Errorf("failed to get recent notifications from cache: %w", err)
	}

	if len(results) == 0 {
		return nil, nil // Cache miss
	}

	notifications := make([]*domain.Notification, 0, len(results))
	for _, data := range results {
		var notification domain.Notification
		if err := json.Unmarshal([]byte(data), &notification); err != nil {
			return nil, fmt.Errorf("failed to unmarshal notification: %w", err)
		}
		notifications = append(notifications, &notification)
	}

	return notifications, nil
}

// PopulateRecentList populates the recent notifications ZSET in Redis
func (r *RedisCache) PopulateRecentList(ctx context.Context, notifications []*domain.Notification) error {
	if len(notifications) == 0 {
		return nil
	}

	pipe := r.client.Pipeline()

	// Add all notifications to ZSET with creation time as score
	for _, notification := range notifications {
		data, err := json.Marshal(notification)
		if err != nil {
			return fmt.Errorf("failed to marshal notification: %w", err)
		}

		score := float64(notification.CreatedAt.UnixMilli())
		pipe.ZAdd(ctx, r.recentListKey, redis.Z{
			Score:  score,
			Member: data,
		})
	}

	// Keep only top 10 (remove everything except the highest 10 scores)
	pipe.ZRemRangeByRank(ctx, r.recentListKey, 0, -11)

	_, err := pipe.Exec(ctx)
	if err != nil {
		return fmt.Errorf("failed to populate recent list: %w", err)
	}

	return nil
}

// ClearRecentList removes the recent notifications list from cache
func (r *RedisCache) ClearRecentList(ctx context.Context) error {
	return r.client.Del(ctx, r.recentListKey).Err()
}

// SetNXWithExpiration sets a key only if it doesn't exist (distributed lock)
func (r *RedisCache) SetNXWithExpiration(ctx context.Context, key, value string, expiration time.Duration) (bool, error) {
	return r.client.SetNX(ctx, key, value, expiration).Result()
}

// DeleteKey deletes a key from Redis
func (r *RedisCache) DeleteKey(ctx context.Context, key string) error {
	return r.client.Del(ctx, key).Err()
}

// GetLockKey generates a lock key for a given operation
func (r *RedisCache) GetLockKey(operation string) string {
	return fmt.Sprintf("%s:%s:lock", r.recentListKey, operation)
}

// Close closes the Redis client connection
func (r *RedisCache) Close() error {
	return r.client.Close()
}