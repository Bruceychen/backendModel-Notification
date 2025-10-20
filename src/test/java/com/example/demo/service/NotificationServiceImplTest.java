package com.example.demo.service;

import com.example.demo.dto.NotificationMessage;
import com.example.demo.dto.NotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.enums.NotificationMessageType;
import com.example.demo.enums.NotificationType;
import com.example.demo.model.Notifications;
import com.example.demo.mq.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationServiceImpl 測試")
class NotificationServiceImplTest {

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @Mock
    private RedisUtil redisUtil;

    @Captor
    private ArgumentCaptor<TransactionSynchronization> synchronizationCaptor;

    @Captor
    private ArgumentCaptor<Notifications> notificationCaptor;

    @Captor
    private ArgumentCaptor<NotificationMessage> messageCaptor;

    private final Long TEST_ID = 1L;
    private Notifications testNotification;

    @BeforeEach
    void setUp() {
        testNotification = new Notifications();
        testNotification.setId(TEST_ID);
        testNotification.setRecipient("user123");
        testNotification.setSubject("Test Subject");
        testNotification.setContent("Test Content");
        testNotification.setType(NotificationType.EMAIL);
    }

    @Nested
    @DisplayName("創建通知 (createNotification)")
    class CreateNotificationTests {

        private NotificationRequest testRequest;

        @BeforeEach
        void setUp() {
            testRequest = NotificationRequest.builder()
                    .recipient("user123")
                    .subject("New Alert")
                    .content("System Update")
                    .type(NotificationType.EMAIL)
                    .build();
        }

        @Test
        @DisplayName("成功創建通知 -> 應保存到DB，並在事務提交後執行快取和MQ操作")
        void givenValidRequest_whenCreateNotification_thenSaveAndExecuteAfterCommitActions() {
            // Given
            when(notificationRepository.save(any(Notifications.class))).thenReturn(testNotification);

            try (MockedStatic<TransactionSynchronizationManager> mockedManager = mockStatic(TransactionSynchronizationManager.class)) {
                // Arrange: Mock transaction as active
                mockedManager.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
                mockedManager.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

                // When
                Notifications result = notificationService.createNotification(testRequest);

                // Then: Assertions on the result and DB interaction
                assertThat(result).isNotNull();
                assertThat(result.getId()).isEqualTo(TEST_ID);
                verify(notificationRepository).save(notificationCaptor.capture());
                assertThat(notificationCaptor.getValue().getRecipient()).isEqualTo(testRequest.getRecipient());

                // And: Verify synchronization was registered and capture the callback
                mockedManager.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));

                // Act: Manually trigger the afterCommit callback
                synchronizationCaptor.getValue().afterCommit();

                // Assert: Verify the afterCommit logic was executed
                verify(redisUtil).cacheNotification(testNotification);
                verify(redisUtil).clearRecentList();
                verify(notificationProducer).sendNotification(messageCaptor.capture());
                assertThat(messageCaptor.getValue().getNotificationMessageType()).isEqualTo(NotificationMessageType.CREATE);
            }
        }

        @Test
        @DisplayName("當沒有活躍事務時 -> 應拋出異常")
        void givenNoActiveTransaction_whenCreateNotification_thenThrowException() {
            // Given
            when(notificationRepository.save(any(Notifications.class))).thenReturn(testNotification);

            try (MockedStatic<TransactionSynchronizationManager> mockedManager = mockStatic(TransactionSynchronizationManager.class)) {
                // Arrange: Mock transaction as inactive, which should cause an exception on registration
                mockedManager.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(false);
                mockedManager.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(false);
                mockedManager.when(() -> TransactionSynchronizationManager.registerSynchronization(any(TransactionSynchronization.class)))
                             .thenThrow(new IllegalStateException("Transaction synchronization is not active"));

                // When & Then: Assert that an exception is thrown
                assertThrows(IllegalStateException.class, () -> notificationService.createNotification(testRequest));

                // And: Verify DB save was still called, but no after-commit actions occurred
                verify(notificationRepository).save(any(Notifications.class));
                verifyNoInteractions(notificationProducer, redisUtil);
            }
        }
    }

    @Nested
    @DisplayName("查詢單一通知 (getNotificationById)")
    class GetNotificationByIdTests {

        @Test
        @DisplayName("快取命中 -> 應從快取返回且不查詢DB")
        void givenCacheHit_whenGetNotificationById_thenReturnFromCache() {
            when(redisUtil.findNotificationById(TEST_ID)).thenReturn(Optional.of(testNotification));
            Optional<Notifications> result = notificationService.getNotificationById(TEST_ID);
            assertThat(result).isPresent().contains(testNotification);
            verify(notificationRepository, never()).findById(anyLong());
            verify(redisUtil, never()).cacheNotification(any());
        }

        @Test
        @DisplayName("快取未命中但DB命中 -> 應從DB返回並回填快取 (Read-Through)")
        void givenCacheMissAndDbHit_whenGetNotificationById_thenReturnFromDbAndCache() {
            when(redisUtil.findNotificationById(TEST_ID)).thenReturn(Optional.empty());
            when(notificationRepository.findById(TEST_ID)).thenReturn(Optional.of(testNotification));
            Optional<Notifications> result = notificationService.getNotificationById(TEST_ID);
            assertThat(result).isPresent().contains(testNotification);
            verify(notificationRepository).findById(TEST_ID);
            verify(redisUtil).cacheNotification(testNotification);
        }

        @Test
        @DisplayName("快取和DB均未命中 -> 應返回空Optional")
        void givenCacheAndDbMiss_whenGetNotificationById_thenReturnEmpty() {
            when(redisUtil.findNotificationById(TEST_ID)).thenReturn(Optional.empty());
            when(notificationRepository.findById(TEST_ID)).thenReturn(Optional.empty());
            Optional<Notifications> result = notificationService.getNotificationById(TEST_ID);
            assertThat(result).isNotPresent();
            verify(redisUtil, never()).cacheNotification(any());
        }
    }

    @Nested
    @DisplayName("更新通知 (updateNotification)")
    class UpdateNotificationTests {

        private UpdateNotificationRequest updateRequest;

        @BeforeEach
        void setup() {
            updateRequest = new UpdateNotificationRequest();
            updateRequest.setSubject("Updated Subject");
            updateRequest.setContent("Updated Content");
        }

        @Test
        @DisplayName("成功更新通知 -> 應更新DB並在事務提交後清除快取和發送MQ")
        void givenExistingId_whenUpdateNotification_thenUpdateAndClearCacheAfterCommit() {
            // Given
            when(notificationRepository.findNotificationAndLockById(TEST_ID)).thenReturn(Optional.of(testNotification));
            when(notificationRepository.save(any(Notifications.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TransactionSynchronizationManager> mockedManager = mockStatic(TransactionSynchronizationManager.class)) {
                // Arrange
                mockedManager.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
                mockedManager.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

                // When
                Optional<Notifications> result = notificationService.updateNotification(TEST_ID, updateRequest);

                // Then
                assertThat(result).isPresent();
                verify(notificationRepository).findNotificationAndLockById(TEST_ID);
                verify(notificationRepository).save(any(Notifications.class));

                // And: Verify and trigger afterCommit
                mockedManager.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));
                synchronizationCaptor.getValue().afterCommit();

                // Assert: Verify afterCommit logic
                verify(redisUtil).clearRecentList();
                verify(redisUtil).deleteNotification(TEST_ID);
                verify(notificationProducer).sendNotification(messageCaptor.capture());
                assertThat(messageCaptor.getValue().getNotificationMessageType()).isEqualTo(NotificationMessageType.UPDATE);
            }
        }

        @Test
        @DisplayName("當通知ID不存在時 -> 應返回空Optional且不執行任何操作")
        void givenNonExistingId_whenUpdateNotification_thenReturnEmpty() {
            when(notificationRepository.findNotificationAndLockById(TEST_ID)).thenReturn(Optional.empty());
            Optional<Notifications> result = notificationService.updateNotification(TEST_ID, updateRequest);
            assertThat(result).isNotPresent();
            verify(notificationRepository, never()).save(any());
            verifyNoInteractions(notificationProducer);
            verify(redisUtil, never()).clearRecentList();
            verify(redisUtil, never()).deleteNotification(anyLong());
        }
    }

    @Nested
    @DisplayName("刪除通知 (deleteNotification)")
    class DeleteNotificationTests {

        @Test
        @DisplayName("成功刪除通知 -> 應從DB刪除並在事務提交後清除快取和發送MQ")
        void givenExistingId_whenDeleteNotification_thenDeleteAndClearCacheAfterCommit() {
            // Given
            when(notificationRepository.findNotificationAndLockById(TEST_ID)).thenReturn(Optional.of(testNotification));

            try (MockedStatic<TransactionSynchronizationManager> mockedManager = mockStatic(TransactionSynchronizationManager.class)) {
                // Arrange
                mockedManager.when(TransactionSynchronizationManager::isSynchronizationActive).thenReturn(true);
                mockedManager.when(TransactionSynchronizationManager::isActualTransactionActive).thenReturn(true);

                // When
                boolean result = notificationService.deleteNotification(TEST_ID);

                // Then
                assertThat(result).isTrue();
                verify(notificationRepository).deleteById(TEST_ID);

                // And: Verify and trigger afterCommit
                mockedManager.verify(() -> TransactionSynchronizationManager.registerSynchronization(synchronizationCaptor.capture()));
                synchronizationCaptor.getValue().afterCommit();

                // Assert: Verify afterCommit logic
                verify(redisUtil).clearRecentList();
                verify(redisUtil).deleteNotification(TEST_ID);
                verify(notificationProducer).sendNotification(messageCaptor.capture());
                assertThat(messageCaptor.getValue().getNotificationMessageType()).isEqualTo(NotificationMessageType.DELETE);
            }
        }

        @Test
        @DisplayName("當通知ID不存在時 -> 應返回false且不執行任何操作")
        void givenNonExistingId_whenDeleteNotification_thenReturnFalse() {
            when(notificationRepository.findNotificationAndLockById(TEST_ID)).thenReturn(Optional.empty());
            boolean result = notificationService.deleteNotification(TEST_ID);
            assertThat(result).isFalse();
            verify(notificationRepository, never()).deleteById(anyLong());
            verifyNoInteractions(notificationProducer);
        }
    }

    @Nested
    @DisplayName("查詢最近通知 (getRecentNotifications) - 快取防護")
    class GetRecentNotificationsTests {
        private final String LOCK_KEY = "lock:getRecentNotifications";
        private final List<Notifications> notificationList = Collections.singletonList(testNotification);

        @BeforeEach
        void setup() {
            lenient().when(redisUtil.getLockKey("getRecentNotifications")).thenReturn(LOCK_KEY);
        }

        @Test
        @DisplayName("快取命中 -> 應直接從快取返回")
        void givenCacheHit_whenGetRecentNotifications_thenReturnFromCache() {
            when(redisUtil.findRecentNotifications()).thenReturn(notificationList);
            List<Notifications> result = notificationService.getRecentNotifications();
            assertThat(result).isEqualTo(notificationList);
            verify(notificationRepository, never()).findTop10ByOrderByCreatedAtDesc(any());
            verify(redisUtil, never()).setnxWithExpiration(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("快取未命中，成功獲取鎖 -> 應從DB查詢並回填快取 (雙重檢查鎖)")
        void givenCacheMissAndLockAcquired_whenGetRecentNotifications_thenFetchFromDbAndCache() {
            when(redisUtil.findRecentNotifications()).thenReturn(Collections.emptyList());
            when(redisUtil.setnxWithExpiration(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(true);
            when(notificationRepository.findTop10ByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(notificationList);

            List<Notifications> result = notificationService.getRecentNotifications();

            assertThat(result).isEqualTo(notificationList);
            InOrder inOrder = inOrder(redisUtil, notificationRepository);
            inOrder.verify(redisUtil).findRecentNotifications(); // 第一次檢查
            inOrder.verify(redisUtil).setnxWithExpiration(eq(LOCK_KEY), anyString(), any(Duration.class)); // 獲取鎖
            inOrder.verify(redisUtil).findRecentNotifications(); // 第二次檢查
            inOrder.verify(notificationRepository).findTop10ByOrderByCreatedAtDesc(any(PageRequest.class)); // 查DB
            inOrder.verify(redisUtil).populateRecentList(notificationList); // 回填快取
            inOrder.verify(redisUtil).deleteKey(LOCK_KEY); // 釋放鎖
        }

        @Test
        @DisplayName("雙重檢查鎖：在獲取鎖後，發現快取已被其他線程填充 -> 應直接返回快取數據")
        void givenLockAcquiredButCachePopulated_whenGetRecentNotifications_thenReturnFromCache() {
            when(redisUtil.findRecentNotifications())
                    .thenReturn(Collections.emptyList()) // 第一次檢查 miss
                    .thenReturn(notificationList);       // 第二次檢查 hit
            when(redisUtil.setnxWithExpiration(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(true);

            List<Notifications> result = notificationService.getRecentNotifications();

            assertThat(result).isEqualTo(notificationList);
            verify(notificationRepository, never()).findTop10ByOrderByCreatedAtDesc(any());
            verify(redisUtil, never()).populateRecentList(any());
            verify(redisUtil).deleteKey(LOCK_KEY); // 仍需釋放鎖
        }

        @Test
        @DisplayName("快取未命中，獲取鎖失敗 -> 應等待後重試並從快取獲取數據")
        void givenCacheMissAndLockFailed_whenGetRecentNotifications_thenWaitAndRetry() {
            when(redisUtil.setnxWithExpiration(eq(LOCK_KEY), anyString(), any(Duration.class))).thenReturn(false);
            when(redisUtil.findRecentNotifications())
                    .thenReturn(Collections.emptyList()) // 第一次檢查 miss
                    .thenReturn(notificationList);       // 重試後檢查 hit

            List<Notifications> result = notificationService.getRecentNotifications();

            assertThat(result).isEqualTo(notificationList);
            verify(redisUtil, times(1)).setnxWithExpiration(eq(LOCK_KEY), anyString(), any(Duration.class));
            verify(redisUtil, times(2)).findRecentNotifications(); // 初始一次，重試一次
            verify(notificationRepository, never()).findTop10ByOrderByCreatedAtDesc(any());
            verify(redisUtil, never()).populateRecentList(any());
            verify(redisUtil, never()).deleteKey(LOCK_KEY);
        }
    }
}
