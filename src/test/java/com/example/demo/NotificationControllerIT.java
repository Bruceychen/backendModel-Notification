package com.example.demo;

import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.model.Notification;
import com.example.demo.model.NotificationType;
import com.example.demo.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class NotificationControllerIT {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.26");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:6.2.6").withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void testCreateAndGetNotification() {
        // 1. Create Notification
        NotificationRequest request = new NotificationRequest();
        request.setType(NotificationType.EMAIL);
        request.setRecipient("integration-test@example.com");
        request.setSubject("Integration Test");
        request.setContent("This is an integration test.");

        ResponseEntity<NotificationResponse> createResponse = restTemplate.postForEntity("/notifications", request, NotificationResponse.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        NotificationResponse createdDto = createResponse.getBody();
        assertThat(createdDto).isNotNull();
        assertThat(createdDto.getId()).isNotNull();
        assertThat(createdDto.getSubject()).isEqualTo("Integration Test");

        // 2. Verify DB persistence
        Notification fromDb = notificationRepository.findById(createdDto.getId()).orElseThrow();
        assertThat(fromDb.getRecipient()).isEqualTo("integration-test@example.com");

        // 3. Verify Redis Cache (individual key)
        String key = "notification:" + createdDto.getId();
        Notification fromCache = (Notification) redisTemplate.opsForValue().get(key);
        assertThat(fromCache).isNotNull();
        assertThat(fromCache.getId()).isEqualTo(createdDto.getId());

        // 4. Verify Redis Cache (recent list)
        List<Object> recentList = redisTemplate.opsForList().range("recent_notifications", 0, -1);
        assertThat(recentList).hasSize(1);
        assertThat(((Notification) recentList.get(0)).getId()).isEqualTo(createdDto.getId());

        // 5. Get Notification by ID (should hit cache)
        ResponseEntity<NotificationResponse> getResponse = restTemplate.getForEntity("/notifications/" + createdDto.getId(), NotificationResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getId()).isEqualTo(createdDto.getId());
    }
}
