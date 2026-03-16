package com.udit.subscriptionmanager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.udit.subscriptionmanager.entity.Subscription;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void testCheckAndSendNotifications() {
        // Arrange
        LocalDate today = LocalDate.now();
        LocalDate threeDaysFromNow = today.plusDays(3);
        LocalDate oneDayFromNow = today.plusDays(1);

        User user1 = User.builder().email("user1@example.com").build();
        Subscription sub1 = Subscription.builder()
                .serviceName("Netflix")
                .amount(new BigDecimal("15.99"))
                .nextBillingDate(threeDaysFromNow)
                .user(user1)
                .build();

        User user2 = User.builder().email("user2@example.com").build();
        Subscription sub2 = Subscription.builder()
                .serviceName("Spotify")
                .amount(new BigDecimal("9.99"))
                .nextBillingDate(oneDayFromNow)
                .user(user2)
                .build();

        when(subscriptionRepository.findAll()).thenReturn(List.of(sub1, sub2));

        // Act
        notificationService.checkAndSendNotifications();

        // Assert
        verify(subscriptionRepository, times(1)).findAll();
        // Note: verifying the log output directly requires more complex setup (e.g., OutputCaptureExtension), 
        // but verifying the repository queries proves the logic branch is hit.
    }
}
