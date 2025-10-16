package com.example.demo.service;


import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notification;
import com.example.demo.mq.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @Mock
    private RedisUtil redisUtil;

    // 用於捕獲註冊到 Spring 事務的同步對象
    @Captor
    private ArgumentCaptor<TransactionSynchronization> synchronizationCaptor;

    private Notification testNotification;
    private NotificationRequest testRequest;
    private UpdateNotificationRequest updateRequest;

    private final Long TEST_ID = 1L;

    @BeforeEach
    void setUp() {
        // 測試數據設置
        testNotification = new Notification();
        testNotification.setId(TEST_ID);
        testNotification.setSubject("Test Subject");
        testNotification.setContent("Test Content");
        testNotification.setType(NotificationType.EMAIL);

        testRequest = NotificationRequest.builder()
                .recipient("user123")
                .subject("New Alert")
                .content("System Update")
                .type(NotificationType.EMAIL)
                .build();

        updateRequest = new UpdateNotificationRequest();
        updateRequest.setSubject("Updated Subject");
        updateRequest.setContent("Updated Content");

    }

    @Test
    @DisplayName("Create: 成功創建通知，並在提交後執行快取和MQ操作")
    void createNotification_Success_ExecutesAfterCommit() {
        // 模擬 DB 行為
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // 模擬 Spring 事務同步管理器
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // 執行 SUT (System Under Test)
            Notification result = notificationService.createNotification(testRequest);

            // 驗證 DB 保存被調用
            verify(notificationRepository, times(1)).save(any(Notification.class));

            // 捕獲註冊的同步對象
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));
            TransactionSynchronization synchronization = synchronizationCaptor.getValue();

            // 模擬事務成功提交 (手動調用 afterCommit)
            synchronization.afterCommit();

            // 驗證 afterCommit 內的邏輯被調用
            verify(notificationProducer, times(1)).sendNotification(any());
            verify(redisUtil, times(1)).cacheNotification(testNotification);
            verify(redisUtil, times(1)).clearRecentList();

            // 驗證結果
            assertEquals(testNotification.getId(), result.getId());
        }
    }


    @Test
    @DisplayName("GetById: 快取命中，不查詢DB")
    void getNotificationById_CacheHit_ReturnsFromCache() {
        when(redisUtil.findNotificationById(TEST_ID)).thenReturn(Optional.of(testNotification));

        Optional<Notification> result = notificationService.getNotificationById(TEST_ID);

        assertTrue(result.isPresent());
        assertEquals(TEST_ID, result.get().getId());

        // 驗證：DB 查詢和快取回填未被調用
        verify(notificationRepository, never()).findById(anyLong());
        verify(redisUtil, never()).cacheNotification(any());
    }

    @Test
    @DisplayName("GetById: 快取Miss但DB命中，從DB返回並回填快取")
    void getNotificationById_CacheMissDbHit_ReturnsFromDbAndCaches() {
        when(redisUtil.findNotificationById(TEST_ID)).thenReturn(Optional.empty());
        when(notificationRepository.findById(TEST_ID)).thenReturn(Optional.of(testNotification));

        Optional<Notification> result = notificationService.getNotificationById(TEST_ID);

        assertTrue(result.isPresent());

        // 驗證：DB 查詢被調用，且快取回填被調用 (Read-Repair)
        verify(notificationRepository, times(1)).findById(TEST_ID);
        verify(redisUtil, times(1)).cacheNotification(testNotification);
    }


    @Test
    @DisplayName("Update: 成功更新，並在提交後執行快取清除操作")
    void updateNotification_Success_ExecutesAfterCommit() {
        // 模擬 DB 行為
        Notification oldNotification = new Notification();
        oldNotification.setId(TEST_ID);

        Notification updatedNotification = new Notification();
        updatedNotification.setId(TEST_ID);
        updatedNotification.setSubject(updateRequest.getSubject()); // 模擬更新後的內容

        when(notificationRepository.findById(TEST_ID)).thenReturn(Optional.of(oldNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(updatedNotification);

        // 模擬 Spring 事務同步管理器
        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // 執行 SUT
            Optional<Notification> result = notificationService.updateNotification(TEST_ID, updateRequest);

            // 驗證 DB 查詢和保存
            assertTrue(result.isPresent());
            verify(notificationRepository, times(1)).findById(TEST_ID);
            verify(notificationRepository, times(1)).save(oldNotification); // 驗證傳入的是舊對象但內容已修改

            // 捕獲註冊的同步對象
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));
            TransactionSynchronization synchronization = synchronizationCaptor.getValue();

            // 模擬事務成功提交 (手動調用 afterCommit)
            synchronization.afterCommit();

            // 驗證 afterCommit 內的邏輯被調用
            verify(redisUtil, times(1)).clearRecentList(); // 驗證 Recent List 被清除
            verify(redisUtil, times(1)).deleteNotification(TEST_ID); // 驗證單一快取被清除
        }
    }

    @Test
    @DisplayName("Delete: ID存在，成功刪除並在提交後清除快取")
    void deleteNotification_IdExists_ExecutesAfterCommit() {
        when(notificationRepository.existsById(TEST_ID)).thenReturn(true);

        try (MockedStatic<TransactionSynchronizationManager> mockedStatic = mockStatic(TransactionSynchronizationManager.class)) {
            // 執行 SUT
            boolean result = notificationService.deleteNotification(TEST_ID);

            // 驗證 DB 刪除被調用
            assertTrue(result);
            verify(notificationRepository, times(1)).deleteById(TEST_ID);

            // 捕獲註冊的同步對象
            mockedStatic.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));
            TransactionSynchronization synchronization = synchronizationCaptor.getValue();

            // 模擬事務成功提交 (手動調用 afterCommit)
            synchronization.afterCommit();

            // 驗證 afterCommit 內的邏輯被調用
            verify(redisUtil, times(1)).clearRecentList();
            verify(redisUtil, times(1)).deleteNotification(TEST_ID);
        }
    }

    @Test
    @DisplayName("Delete: ID不存在，返回false且不執行DB刪除")
    void deleteNotification_IdNotExists_ReturnsFalse() {
        when(notificationRepository.existsById(TEST_ID)).thenReturn(false);

        boolean result = notificationService.deleteNotification(TEST_ID);

        assertFalse(result);
        verify(notificationRepository, never()).deleteById(anyLong());
        // 驗證沒有嘗試註冊 afterCommit
        verifyNoInteractions(redisUtil);
    }


    @Test
    @DisplayName("GetRecent: 快取命中，直接返回")
    void getRecentNotifications_CacheHit_ReturnsFromCache() {
        List<Notification> cachedList = Collections.singletonList(testNotification);
        when(redisUtil.findRecentNotifications()).thenReturn(cachedList);

        List<Notification> result = notificationService.getRecentNotifications();

        assertFalse(result.isEmpty());
        // 驗證：沒有任何鎖操作、DB查詢或回填
        verify(redisUtil, never()).setnxWithExpiration(anyString(), anyString(), any(Duration.class));
        verify(notificationRepository, never()).findTop10ByOrderByCreatedAtDesc(any(PageRequest.class));
    }

    @Test
    @DisplayName("GetRecent: 快取Miss，獲取鎖成功，從DB獲取並回填快取")
    void getRecentNotifications_CacheMiss_LockAcquired_ReturnsFromDbAndCaches() {
        List<Notification> dbList = Collections.singletonList(testNotification);

        // 模擬鎖 Key
        String LOCK_KEY = "lock:getRecentNotifications";
        when(redisUtil.getLockKey("getRecentNotifications")).thenReturn(LOCK_KEY);

        // 1. 初始 Cache Miss
        when(redisUtil.findRecentNotifications()).thenReturn(Collections.emptyList());
        // 2. 鎖獲取成功
        when(redisUtil.setnxWithExpiration(eq(LOCK_KEY), eq("locked"), any(Duration.class))).thenReturn(true);
        // 3. 雙重檢查後 Cache Miss (返回空)
        // 4. DB 查詢成功
        when(notificationRepository.findTop10ByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(dbList);

        List<Notification> result = notificationService.getRecentNotifications();

        // 驗證流程
        assertTrue(result.containsAll(dbList));
        verify(redisUtil, times(1)).setnxWithExpiration(eq(LOCK_KEY), eq("locked"), any(Duration.class));
        verify(notificationRepository, times(1)).findTop10ByOrderByCreatedAtDesc(any(PageRequest.class));
        verify(redisUtil, times(1)).populateRecentList(dbList); // 驗證回填
        verify(redisUtil, times(1)).deleteKey(LOCK_KEY); // 驗證鎖被釋放
    }

}
