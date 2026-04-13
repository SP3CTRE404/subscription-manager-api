package com.udit.subscriptionmanager.service;

import com.udit.subscriptionmanager.dto.HouseholdResponse;
import com.udit.subscriptionmanager.dto.MemberResponse;
import com.udit.subscriptionmanager.entity.Household;
import com.udit.subscriptionmanager.entity.Subscription;
import com.udit.subscriptionmanager.entity.User;
import com.udit.subscriptionmanager.repository.HouseholdRepository;
import com.udit.subscriptionmanager.repository.SubscriptionRepository;
import com.udit.subscriptionmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.udit.subscriptionmanager.exception.BadRequestException;
import com.udit.subscriptionmanager.exception.ResourceNotFoundException;
import com.udit.subscriptionmanager.exception.UnauthorizedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;
import jakarta.persistence.EntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntityManager entityManager;

    @Transactional
    public HouseholdResponse createHousehold(User user, String name) {
        if (user.getHousehold() != null) {
            throw new BadRequestException(
                    "User already belongs to a household. Leave it first before creating a new one.");
        }

        if (name == null || name.isBlank()) {
            throw new BadRequestException("Household name is required.");
        }

        Household household = Household.builder()
                .name(name)
                .createdAt(LocalDateTime.now())
                .admin(user)
                .inviteCode(generateInviteCode())
                .build();

        Household saved = householdRepository.save(java.util.Objects.requireNonNull(household));

        user.setHousehold(saved);
        userRepository.save(user);

        log.info("User '{}' created household '{}'", user.getEmail(), name);
        return convertToResponse(saved);
    }

    @Transactional
    public HouseholdResponse joinHousehold(User user, String inviteCode) {
        if (user.getHousehold() != null) {
            throw new BadRequestException(
                    "User already belongs to a household. Leave it first before joining another.");
        }

        Household household = householdRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code. No household found."));

        user.setHousehold(household);
        userRepository.save(user);

        log.info("User '{}' joined household '{}'", user.getEmail(), household.getName());
        return convertToResponse(household);
    }

    @Transactional
    public void leaveHousehold(User user) {
        Household household = user.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        // Admin cannot leave without transferring admin first
        if (household.getAdmin() != null && household.getAdmin().getId().equals(user.getId())) {
            throw new BadRequestException("You are the admin. Transfer admin role to another member before leaving.");
        }

        user.setHousehold(null);
        userRepository.save(user);

        log.info("User '{}' left household '{}'", user.getEmail(), household.getName());
    }

    @Transactional
    public HouseholdResponse transferAdmin(User currentAdmin, @NonNull Long newAdminId) {
        Household household = currentAdmin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(currentAdmin.getId())) {
            throw new UnauthorizedException("Only the current admin can transfer admin rights.");
        }

        User newAdmin = userRepository.findById(java.util.Objects.requireNonNull(newAdminId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (newAdmin.getHousehold() == null || !java.util.Objects.equals(newAdmin.getHousehold().getId(), household.getId())) {
            throw new BadRequestException("The new admin must be a member of this household.");
        }

        if (newAdmin.getDateOfBirth() != null) {
            int age = java.time.Period.between(newAdmin.getDateOfBirth(), java.time.LocalDate.now()).getYears();
            if (age < 18) {
                throw new BadRequestException("Users under 18 cannot be household administrators.");
            }
        }

        household.setAdmin(newAdmin);
        householdRepository.save(household);

        // Ensure the changes are flushed and the new admin's relationship is refreshed
        entityManager.flush();
        entityManager.refresh(newAdmin);

        log.info("Admin of household '{}' transferred from '{}' to '{}'",
                household.getName(), currentAdmin.getEmail(), newAdmin.getEmail());
        return convertToResponse(household);
    }

    @Transactional
    public void deleteHousehold(User admin) {
        Household household = admin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(admin.getId())) {
            throw new UnauthorizedException("Only the admin can delete the household.");
        }

        // Unlink all members from the household
        List<User> members = userRepository.findByHouseholdId(household.getId());
        for (User member : members) {
            member.setHousehold(null);
            userRepository.save(member);
        }

        // Unlink subscriptions from the household (keep them as personal subs)
        List<Subscription> householdSubs = subscriptionRepository.findByHouseholdId(household.getId());
        for (Subscription sub : householdSubs) {
            sub.setHousehold(null);
            subscriptionRepository.save(sub);
        }

        householdRepository.delete(household);

        log.info("Household '{}' deleted by admin '{}'", household.getName(), admin.getEmail());
    }

    @Transactional
    public void removeMember(User admin, @NonNull Long memberId) {
        Household household = admin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(admin.getId())) {
            throw new UnauthorizedException("Only the admin can remove members.");
        }

        if (admin.getId().equals(memberId)) {
            throw new BadRequestException("You cannot remove yourself. Use the Leave or Delete flow instead.");
        }

        User member = userRepository.findById(java.util.Objects.requireNonNull(memberId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found."));

        if (member.getHousehold() == null || !java.util.Objects.equals(member.getHousehold().getId(), household.getId())) {
            throw new BadRequestException("This user is not a member of your household.");
        }

        member.setHousehold(null);
        userRepository.save(member);

        log.info("Member '{}' removed from household '{}' by admin '{}'",
                member.getEmail(), household.getName(), admin.getEmail());
    }

    @Transactional
    public HouseholdResponse editName(User admin, String newName) {
        Household household = admin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(admin.getId())) {
            throw new UnauthorizedException("Only the admin can edit the household name.");
        }

        if (newName == null || newName.isBlank()) {
            throw new BadRequestException("Household name cannot be empty.");
        }

        household.setName(newName);
        Household saved = householdRepository.save(household);

        log.info("Household name updated to '{}' by admin '{}'", newName, admin.getEmail());
        return convertToResponse(saved);
    }

    @Transactional
    public HouseholdResponse updateImage(User admin, String imageUrl) {
        Household household = admin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(admin.getId())) {
            throw new UnauthorizedException("Only the admin can edit the household image.");
        }

        household.setImageUrl(imageUrl);
        Household saved = householdRepository.save(household);

        log.info("Household image updated by admin '{}'", admin.getEmail());
        return convertToResponse(saved);
    }

    @Transactional
    public HouseholdResponse regenerateInviteCode(User admin) {
        Household household = admin.getHousehold();
        if (household == null) {
            throw new BadRequestException("User does not belong to any household.");
        }

        if (household.getAdmin() == null || !household.getAdmin().getId().equals(admin.getId())) {
            throw new UnauthorizedException("Only the admin can regenerate the invite code.");
        }

        household.setInviteCode(generateInviteCode());
        Household saved = householdRepository.save(household);

        log.info("Invite code regenerated for household '{}' by admin '{}'", household.getName(), admin.getEmail());
        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(@NonNull Long householdId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResourceNotFoundException("Household not found"));
        Long adminId = household.getAdmin() != null ? household.getAdmin().getId() : null;

        List<User> members = userRepository.findByHouseholdId(householdId);
        return members.stream()
                .map(m -> MemberResponse.builder()
                        .id(m.getId())
                        .fullName(m.getFullName())
                        .email(m.getEmail())
                        .role(adminId != null && adminId.equals(m.getId()) ? "ADMIN" : "MEMBER")
                        .profilePicture(m.getProfilePicture())
                        .dateOfBirth(m.getDateOfBirth())
                        .build())

                .toList();
    }

    @Transactional(readOnly = true)
    public HouseholdResponse getHouseholdForUser(User user) {
        Household household = user.getHousehold();
        if (household == null) {
            throw new ResourceNotFoundException("User does not belong to any household.");
        }
        return convertToResponse(household);
    }

    private HouseholdResponse convertToResponse(Household household) {
        List<MemberResponse> members = userRepository.findByHouseholdId(household.getId())
                .stream()
                .map(m -> MemberResponse.builder()
                        .id(m.getId())
                        .fullName(m.getFullName())
                        .email(m.getEmail())
                        .role(household.getAdmin() != null && household.getAdmin().getId().equals(m.getId()) ? "ADMIN"
                                : "MEMBER")
                        .profilePicture(m.getProfilePicture())
                        .dateOfBirth(m.getDateOfBirth())
                        .build())

                .toList();

        return HouseholdResponse.builder()
                .id(household.getId())
                .name(household.getName())
                .inviteCode(household.getInviteCode())
                .adminId(household.getAdmin() != null ? household.getAdmin().getId() : null)
                .adminName(household.getAdmin() != null ? household.getAdmin().getFullName() : null)
                .createdAt(household.getCreatedAt())
                .imageUrl(household.getImageUrl()) // NEW
                .members(members)
                .build();
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
