package com.example.demo.controller;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.model.Notifications;
import com.example.demo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(@RequestBody NotificationRequest request) {
        Notifications createdNotification = notificationService.createNotification(request);
        return new ResponseEntity<>(NotificationResponse.fromEntity(createdNotification), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getNotificationById(@PathVariable Long id){
        Optional<Notifications> notifications = notificationService.getNotificationById(id);
        if (notifications.isPresent()) {
            return ResponseEntity.ok(NotificationResponse.fromEntity(notifications.get()));
        } else {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "data is not existed");
            return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<List<NotificationResponse>> getRecentNotifications() {
        List<Notifications> recentNoti = notificationService.getRecentNotifications();
        List<NotificationResponse> response =  recentNoti.stream()
                .map(NotificationResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNotification(@PathVariable Long id, @RequestBody UpdateNotificationRequest request){
        return null;
    }

    @DeleteMapping("{id}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long id){
        return null;
    }
}
