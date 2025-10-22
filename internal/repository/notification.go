package repository

import (
	"context"
	"fmt"

	"github.com/brucechen/notification-service/internal/config"
	"github.com/brucechen/notification-service/internal/domain"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
	"gorm.io/gorm/logger"
)

// NotificationRepository handles database operations for notifications
type NotificationRepository struct {
	db *gorm.DB
}

// NewNotificationRepository creates a new NotificationRepository
func NewNotificationRepository(cfg *config.Config) (*NotificationRepository, error) {
	db, err := gorm.Open(mysql.Open(cfg.Database.GetDSN()), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Info),
	})
	if err != nil {
		return nil, fmt.Errorf("failed to connect to database: %w", err)
	}

	// Auto-migrate the schema
	if err := db.AutoMigrate(&domain.Notification{}); err != nil {
		return nil, fmt.Errorf("failed to migrate database: %w", err)
	}

	return &NotificationRepository{db: db}, nil
}

// Save creates or updates a notification
func (r *NotificationRepository) Save(ctx context.Context, notification *domain.Notification) error {
	return r.db.WithContext(ctx).Save(notification).Error
}

// FindByID retrieves a notification by ID
func (r *NotificationRepository) FindByID(ctx context.Context, id int64) (*domain.Notification, error) {
	var notification domain.Notification
	err := r.db.WithContext(ctx).First(&notification, id).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &notification, nil
}

// FindNotificationAndLockByID retrieves a notification by ID with pessimistic write lock
// This prevents concurrent modifications during UPDATE and DELETE operations
func (r *NotificationRepository) FindNotificationAndLockByID(ctx context.Context, id int64) (*domain.Notification, error) {
	var notification domain.Notification
	err := r.db.WithContext(ctx).
		Clauses(clause.Locking{Strength: "UPDATE"}). // SELECT ... FOR UPDATE
		First(&notification, id).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &notification, nil
}

// FindTop10ByOrderByCreatedAtDesc retrieves the 10 most recent notifications
func (r *NotificationRepository) FindTop10ByOrderByCreatedAtDesc(ctx context.Context) ([]*domain.Notification, error) {
	var notifications []*domain.Notification
	err := r.db.WithContext(ctx).
		Order("created_at DESC").
		Limit(10).
		Find(&notifications).Error
	if err != nil {
		return nil, err
	}
	return notifications, nil
}

// DeleteByID deletes a notification by ID
func (r *NotificationRepository) DeleteByID(ctx context.Context, id int64) error {
	return r.db.WithContext(ctx).Delete(&domain.Notification{}, id).Error
}

// BeginTx starts a new transaction and returns a new repository instance with the transaction
func (r *NotificationRepository) BeginTx(ctx context.Context) (*NotificationRepository, error) {
	tx := r.db.WithContext(ctx).Begin()
	if tx.Error != nil {
		return nil, tx.Error
	}
	return &NotificationRepository{db: tx}, nil
}

// Commit commits the current transaction
func (r *NotificationRepository) Commit() error {
	return r.db.Commit().Error
}

// Rollback rolls back the current transaction
func (r *NotificationRepository) Rollback() error {
	return r.db.Rollback().Error
}

// GetDB returns the underlying database connection (for transaction management)
func (r *NotificationRepository) GetDB() *gorm.DB {
	return r.db
}

// Close closes the database connection
func (r *NotificationRepository) Close() error {
	sqlDB, err := r.db.DB()
	if err != nil {
		return err
	}
	return sqlDB.Close()
}