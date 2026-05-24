package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.entity.Notification;
import com.udit.subscriptionmanager.entity.Subscription;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.repository.NotificationRepository;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createHouseholdJoinNotification(User joinedUser, Household household) {
        List<User> members = userRepository.findByHouseholdId(household.getId());
        for (User member : members) {
            if (member.getId().equals(joinedUser.getId())) {
                continue; // Do not notify the person who joined
            }
            Notification notification = Notification.builder()
                    .user(member)
                    .title("Member Joined")
                    .message(String.format("%s joined your household %s", joinedUser.getFullName(), household.getName()))
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            log.info("Queued household join notification for user '{}' (member joined: '{}')", member.getEmail(), joinedUser.getEmail());
        }
    }

    @Transactional
    public void createHouseholdLeaveNotification(User leftUser, Household household) {
        List<User> members = userRepository.findByHouseholdId(household.getId());
        for (User member : members) {
            if (member.getId().equals(leftUser.getId())) {
                continue; // Do not notify the person who left
            }
            Notification notification = Notification.builder()
                    .user(member)
                    .title("Member Left")
                    .message(String.format("%s left your household %s", leftUser.getFullName(), household.getName()))
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            log.info("Queued household leave notification for user '{}' (member left: '{}')", member.getEmail(), leftUser.getEmail());
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getPendingNotifications(User user) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public void markAsRead(User user, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.udit.subscriptionmanager.exception.ResourceNotFoundException("Notification not found"));
        if (!notification.getUser().getId().equals(user.getId())) {
            throw new com.udit.subscriptionmanager.exception.UnauthorizedException("You do not own this notification");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
        log.info("Marked notification {} as read for user {}", notificationId, user.getEmail());
    }

    @Transactional(readOnly = true)
    public void checkAndSendNotifications() {
        log.info("Starting daily check for upcoming and overdue subscription payments.");

        List<Subscription> allSubscriptions = subscriptionRepository.findAll();

        for (Subscription sub : allSubscriptions) {
            if (sub.getNextBillingDate() == null) continue;

            // Strict Payer Model: Notifications go directly to the user who owns it
            User targetUser = sub.getUser();
            if (targetUser == null) continue;

            // Safe Timezone Parsing (Prevents the 8:00 AM job from crashing if a timezone is invalid)
            String timeZoneStr = targetUser.getTimeZone() != null ? targetUser.getTimeZone() : "UTC";
            ZoneId zoneId;
            try {
                zoneId = ZoneId.of(timeZoneStr);
            } catch (Exception e) {
                log.warn("Invalid timezone '{}' for user {}. Falling back to UTC.", timeZoneStr, targetUser.getEmail());
                zoneId = ZoneId.of("UTC");
            }

            LocalDate userToday = LocalDate.now(zoneId);
            long daysUntilBilling = java.time.temporal.ChronoUnit.DAYS.between(userToday, sub.getNextBillingDate());

            if (daysUntilBilling == 3 || daysUntilBilling == 1) {
                notifyUser(sub, (int) daysUntilBilling, "Upcoming");
            } else if (daysUntilBilling == 0) {
                notifyUser(sub, 0, "Due Today");
            } else if (daysUntilBilling < 0 && (sub.getIsAutoPay() != null && !sub.getIsAutoPay())) {

                // Overdue Nag: Force notification on Day 1 late, and then every 3 days after that
                if (daysUntilBilling == -1 || Math.abs(daysUntilBilling) % 3 == 0) {
                    notifyUser(sub, (int) daysUntilBilling, "OVERDUE");
                }
            }
        }

        log.info("Finished daily check for subscription payments.");
    }

    private void notifyUser(Subscription sub, int daysLeft, String type) {
        String email = sub.getUser().getEmail();
        String message;

        // Accurate wording for Auto vs Manual subscriptions
        boolean isAuto = sub.getIsAutoPay() != null && sub.getIsAutoPay();
        String action = isAuto ? "will automatically charge" : "requires a manual payment of";

        if (type.equals("Due Today")) {
            message = String.format("Notification for [%s]: Your subscription for '%s' ($%s) is DUE TODAY.",
                    email, sub.getServiceName(), sub.getAmount());
        } else if (type.equals("OVERDUE")) {
            int overdueDays = Math.abs(daysLeft);
            message = String.format("URGENT for [%s]: Your manual subscription for '%s' ($%s) is OVERDUE by %d day%s. Please mark it as paid.",
                    email, sub.getServiceName(), sub.getAmount(), overdueDays, overdueDays == 1 ? "" : "s");
        } else {
            message = String.format("Notification for [%s]: Your subscription for '%s' %s $%s on %s (in %d day%s).",
                    email, sub.getServiceName(), action, sub.getAmount(), sub.getNextBillingDate(), daysLeft, daysLeft == 1 ? "" : "s");
        }

        // Simulation removed as per request

    }
}