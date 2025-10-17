package com.example.demo.controller;


import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    @GetMapping("/{id}")
    public ResponseEntity<?> getNotificationById(@PathVariable Long id){
        return null;
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
