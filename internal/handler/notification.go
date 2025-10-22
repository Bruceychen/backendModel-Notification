package handler

import (
	"net/http"
	"strconv"

	"github.com/brucechen/notification-service/internal/dto"
	"github.com/brucechen/notification-service/internal/service"
	"github.com/gin-gonic/gin"
)

// NotificationHandler handles HTTP requests for notifications
type NotificationHandler struct {
	service service.NotificationService
}

// NewNotificationHandler creates a new NotificationHandler
func NewNotificationHandler(service service.NotificationService) *NotificationHandler {
	return &NotificationHandler{
		service: service,
	}
}

// RegisterRoutes registers all notification routes
func (h *NotificationHandler) RegisterRoutes(router *gin.Engine) {
	notifications := router.Group("/notifications")
	{
		notifications.POST("", h.CreateNotification)
		notifications.GET("/:id", h.GetNotificationByID)
		notifications.GET("/recent", h.GetRecentNotifications)
		notifications.PUT("/:id", h.UpdateNotification)
		notifications.DELETE("/:id", h.DeleteNotification)
	}
}

// CreateNotification handles POST /notifications
func (h *NotificationHandler) CreateNotification(c *gin.Context) {
	var req dto.NotificationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid request body: " + err.Error(),
		})
		return
	}

	// Validate notification type
	if !req.Type.IsValid() {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid notification type",
		})
		return
	}

	notification, err := h.service.CreateNotification(c.Request.Context(), &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, dto.ErrorResponse{
			Message: "Failed to create notification: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusCreated, dto.FromEntity(notification))
}

// GetNotificationByID handles GET /notifications/:id
func (h *NotificationHandler) GetNotificationByID(c *gin.Context) {
	idStr := c.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid notification ID",
		})
		return
	}

	notification, err := h.service.GetNotificationByID(c.Request.Context(), id)
	if err != nil {
		c.JSON(http.StatusInternalServerError, dto.ErrorResponse{
			Message: "Failed to get notification: " + err.Error(),
		})
		return
	}

	if notification == nil {
		c.JSON(http.StatusNotFound, dto.ErrorResponse{
			Message: "data is not existed",
		})
		return
	}

	c.JSON(http.StatusOK, dto.FromEntity(notification))
}

// GetRecentNotifications handles GET /notifications/recent
func (h *NotificationHandler) GetRecentNotifications(c *gin.Context) {
	notifications, err := h.service.GetRecentNotifications(c.Request.Context())
	if err != nil {
		c.JSON(http.StatusInternalServerError, dto.ErrorResponse{
			Message: "Failed to get recent notifications: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, dto.FromEntities(notifications))
}

// UpdateNotification handles PUT /notifications/:id
func (h *NotificationHandler) UpdateNotification(c *gin.Context) {
	idStr := c.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid notification ID",
		})
		return
	}

	var req dto.UpdateNotificationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid request body: " + err.Error(),
		})
		return
	}

	notification, err := h.service.UpdateNotification(c.Request.Context(), id, &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, dto.ErrorResponse{
			Message: "Failed to update notification: " + err.Error(),
		})
		return
	}

	if notification == nil {
		c.JSON(http.StatusNotFound, dto.ErrorResponse{
			Message: "data is not existed",
		})
		return
	}

	c.JSON(http.StatusOK, dto.FromEntity(notification))
}

// DeleteNotification handles DELETE /notifications/:id
func (h *NotificationHandler) DeleteNotification(c *gin.Context) {
	idStr := c.Param("id")
	id, err := strconv.ParseInt(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, dto.ErrorResponse{
			Message: "Invalid notification ID",
		})
		return
	}

	deleted, err := h.service.DeleteNotification(c.Request.Context(), id)
	if err != nil {
		c.JSON(http.StatusInternalServerError, dto.ErrorResponse{
			Message: "Failed to delete notification: " + err.Error(),
		})
		return
	}

	if !deleted {
		c.JSON(http.StatusNotFound, dto.ErrorResponse{
			Message: "data is not existed",
		})
		return
	}

	c.Status(http.StatusNoContent)
}