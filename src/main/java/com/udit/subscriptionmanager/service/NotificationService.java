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

    @Scheduled(cron = "0 0 8 * * ?")
    @Transactional(readOnly = true)
    public void checkAndSendNotifications() {
        log.info("Starting daily check for upcoming and overdue subscription payments.");

        List<Subscription> allSubscriptions = subscriptionRepository.findAll();

        for (Subscription sub : allSubscriptions) {
            if (sub.getNextBillingDate() == null) continue;

            // Updated to the Strict Payer Model: Notifications go directly to the user who owns it
            User targetUser = sub.getUser();
            if (targetUser == null) continue;

            String timeZoneStr = targetUser.getTimeZone() != null ? targetUser.getTimeZone() : "UTC";
            java.time.ZoneId zoneId = java.time.ZoneId.of(timeZoneStr);
            LocalDate userToday = LocalDate.now(zoneId);

            long daysUntilBilling = java.time.temporal.ChronoUnit.DAYS.between(userToday, sub.getNextBillingDate());

            if (daysUntilBilling == 3 || daysUntilBilling == 1) {
                notifyUser(sub, (int) daysUntilBilling, "Upcoming");
            } else if (daysUntilBilling == 0) {
                notifyUser(sub, 0, "Due Today");
            } else if (daysUntilBilling < 0 && (sub.getIsAutoPay() != null && !sub.getIsAutoPay())) {
                // Overdue Nag: Remind them every 3 days if a manual bill is past due
                if (Math.abs(daysUntilBilling) % 3 == 0) {
                    notifyUser(sub, (int) daysUntilBilling, "OVERDUE");
                }
            }
        }

        log.info("Finished daily check for subscription payments.");
    }

    private void notifyUser(Subscription sub, int daysLeft, String type) {
        String email = sub.getUser().getEmail();
        String message;

        if (type.equals("Due Today")) {
            message = String.format("Notification for [%s]: Your subscription for '%s' ($%s) is DUE TODAY.",
                    email, sub.getServiceName(), sub.getAmount());
        } else if (type.equals("OVERDUE")) {
            message = String.format("URGENT for [%s]: Your manual subscription for '%s' ($%s) is OVERDUE by %d days. Please take necessary actions and mark it as paid.",
                    email, sub.getServiceName(), sub.getAmount(), Math.abs(daysLeft));
        } else {
            message = String.format("Notification for [%s]: Your subscription for '%s' will charge $%s on %s (in %d day%s).",
                    email, sub.getServiceName(), sub.getAmount(), sub.getNextBillingDate(), daysLeft, daysLeft == 1 ? "" : "s");
        }

        log.info("--- EMAIL SIMULATION ({}) --- {}", type, message);
    }
}