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

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

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

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(@RequestBody NotificationRequest request) {
        return null;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<NotificationResponse>> getRecentNotifications(){
        return null;
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
