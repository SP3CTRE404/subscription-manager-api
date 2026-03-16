package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.entity.Subscription;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Checks for subscriptions due in 3 days and 1 day and sends notifications.
     * Runs daily at 8:00 AM server time.
     */
    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional(readOnly = true)
    public void checkAndSendNotifications() {
        log.info("Starting daily check for upcoming subscription payments.");
        
        // Fetch ALL subscriptions rather than those with specific dates since 
        // the definition of "today" varies per user timezone.
        // Assuming we want active subscriptions, we can just grab all or paginate.
        // For efficiency, you might want to only fetch subscriptions due in the next ~5 days UTC, 
        // but for safety in this model we'll check against active subscriptions.
        List<Subscription> allSubscriptions = subscriptionRepository.findAll();

        for (Subscription sub : allSubscriptions) {
            if (sub.getNextBillingDate() == null) continue;

            User targetUser = sub.getUser() != null ? sub.getUser() : 
                    (sub.getHousehold() != null ? sub.getHousehold().getAdmin() : null);

            if (targetUser == null) continue;

            String timeZoneStr = targetUser.getTimeZone() != null ? targetUser.getTimeZone() : "UTC";
            java.time.ZoneId zoneId = java.time.ZoneId.of(timeZoneStr);
            LocalDate userToday = LocalDate.now(zoneId);

            long daysUntilBilling = java.time.temporal.ChronoUnit.DAYS.between(userToday, sub.getNextBillingDate());

            if (daysUntilBilling == 3 || daysUntilBilling == 1) {
                notifyUsers(List.of(sub), (int) daysUntilBilling);
            }
        }

        log.info("Finished daily check for upcoming subscription payments.");
    }

    private void notifyUsers(List<Subscription> subscriptions, int daysLeft) {
        for (Subscription sub : subscriptions) {
            String email = null;
            if (sub.getUser() != null) {
                email = sub.getUser().getEmail();
            } else if (sub.getHousehold() != null && sub.getHousehold().getAdmin() != null) {
                // If it's a household subscription, notify the household admin
                email = sub.getHousehold().getAdmin().getEmail();
            }

            if (email != null) {
                // Formatting simulated email content
                String message = String.format(
                        "Notification for [%s]: Your subscription for '%s' will charge $%s on %s (in %d day%s).",
                        email,
                        sub.getServiceName(),
                        sub.getAmount(),
                        sub.getNextBillingDate(),
                        daysLeft,
                        daysLeft == 1 ? "" : "s"
                );
                log.info("--- EMAIL SIMULATION --- {}", message);
            } else {
                log.warn("Could not determine email to notify for subscription ID: {}", sub.getId());
            }
        }
    }
}
