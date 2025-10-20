package com.example.demo.controller;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import com.example.demo.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @TestConfiguration
    static class ControllerTestConfig {
        @Bean
        public NotificationService notificationService() {
            return Mockito.mock(NotificationService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private Notifications notification;

    @BeforeEach
    void setUp() {
        notification = new Notifications();
        notification.setId(1L);
        notification.setType(NotificationType.EMAIL);
        notification.setRecipient("test@example.com");
        notification.setSubject("Test Subject");
        notification.setContent("Test Content");
        notification.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void createNotification_shouldReturnCreated() throws Exception {
        when(notificationService.createNotification(any(NotificationRequest.class))).thenReturn(notification);

        NotificationRequest request = new NotificationRequest();
        request.setType(NotificationType.EMAIL);
        request.setRecipient("test@example.com");
        request.setSubject("Test Subject");
        request.setContent("Test Content");

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.subject").value("Test Subject"));
    }

    @Test
    void getNotificationById_whenFound_shouldReturnOk() throws Exception {
        when(notificationService.getNotificationById(1L)).thenReturn(Optional.of(notification));

        mockMvc.perform(get("/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void getNotificationById_whenNotFound_shouldReturnNotFound() throws Exception {
        when(notificationService.getNotificationById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/notifications/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getRecentNotifications_shouldReturnOk() throws Exception {
        when(notificationService.getRecentNotifications()).thenReturn(List.of(notification));

        mockMvc.perform(get("/notifications/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void updateNotification_whenFound_shouldReturnOk() throws Exception {
        when(notificationService.updateNotification(eq(1L), any(UpdateNotificationRequest.class))).thenReturn(Optional.of(notification));

        UpdateNotificationRequest request = new UpdateNotificationRequest();
        request.setSubject("Updated Subject");
        request.setContent("Updated Content");

        mockMvc.perform(put("/notifications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void updateNotification_whenNotFound_shouldReturnNotFound() throws Exception {
        when(notificationService.updateNotification(eq(1L), any(UpdateNotificationRequest.class))).thenReturn(Optional.empty());

        UpdateNotificationRequest request = new UpdateNotificationRequest();
        request.setSubject("Updated Subject");
        request.setContent("Updated Content");

        mockMvc.perform(put("/notifications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNotification_whenFound_shouldReturnNoContent() throws Exception {
        when(notificationService.deleteNotification(1L)).thenReturn(true);

        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteNotification_whenNotFound_shouldReturnNotFound() throws Exception {
        when(notificationService.deleteNotification(1L)).thenReturn(false);

        mockMvc.perform(delete("/notifications/1"))
                .andExpect(status().isNotFound());
    }
}
