package com.nexaai.user.service;

import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.model.AccountStatus;
import com.nexaai.user.domain.repository.UserProfileRepository;
import com.nexaai.user.dto.request.UpdateUserStatusRequest;
import com.nexaai.user.dto.response.PageResponse;
import com.nexaai.user.dto.response.SubscriptionInfoResponse;
import com.nexaai.user.dto.response.UsageInfoResponse;
import com.nexaai.user.dto.response.UserProfileResponse;
import com.nexaai.user.dto.response.UserSummaryResponse;
import com.nexaai.user.exception.UserException;
import com.nexaai.user.service.external.SubscriptionBoundaryService;
import com.nexaai.user.service.external.UsageBoundaryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserProfileRepository userProfileRepository;
    private final UserEventPublisher eventPublisher;
    private final SubscriptionBoundaryService subscriptionBoundaryService;
    private final UsageBoundaryService usageBoundaryService;

    public AdminUserService(UserProfileRepository userProfileRepository,
                            UserEventPublisher eventPublisher,
                            SubscriptionBoundaryService subscriptionBoundaryService,
                            UsageBoundaryService usageBoundaryService) {
        this.userProfileRepository = userProfileRepository;
        this.eventPublisher = eventPublisher;
        this.subscriptionBoundaryService = subscriptionBoundaryService;
        this.usageBoundaryService = usageBoundaryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(String query, AccountStatus status, Pageable pageable) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        Page<UserProfile> page = userProfileRepository.searchUsers(cleanQuery, status, pageable);

        List<UserSummaryResponse> items = page.getContent().stream()
                .map(u -> new UserSummaryResponse(
                        u.getId().toString(),
                        u.getEmail(),
                        u.getDisplayName(),
                        u.getAccountStatus().name(),
                        u.getCreatedAt()
                ))
                .toList();

        return new PageResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserDetails(UUID targetUserId) {
        UserProfile user = userProfileRepository.findById(targetUserId)
                .orElseThrow(() -> new UserException("USER_NOT_FOUND", "User profile not found.", HttpStatus.NOT_FOUND));
        return UserService.mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUserStatus(UUID adminUserId, UUID targetUserId, UpdateUserStatusRequest req) {
        UserProfile user = userProfileRepository.findById(targetUserId)
                .orElseThrow(() -> new UserException("USER_NOT_FOUND", "User profile not found.", HttpStatus.NOT_FOUND));

        AccountStatus newStatus;
        try {
            newStatus = AccountStatus.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UserException("INVALID_STATUS", "Invalid account status: " + req.status(), HttpStatus.BAD_REQUEST);
        }

        AccountStatus oldStatus = user.getAccountStatus();
        if (oldStatus != newStatus) {
            user.setAccountStatus(newStatus);
            user.setUpdatedAt(Instant.now());
            user = userProfileRepository.save(user);

            eventPublisher.publishStatusChanged(
                    adminUserId.toString(),
                    targetUserId.toString(),
                    oldStatus.name(),
                    newStatus.name()
            );
        }

        return UserService.mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public SubscriptionInfoResponse getUserSubscription(UUID targetUserId) {
        // Verify user exists first
        if (!userProfileRepository.existsById(targetUserId)) {
            throw new UserException("USER_NOT_FOUND", "User profile not found.", HttpStatus.NOT_FOUND);
        }
        return subscriptionBoundaryService.getSubscriptionForUser(targetUserId);
    }

    @Transactional(readOnly = true)
    public UsageInfoResponse getUserUsage(UUID targetUserId) {
        // Verify user exists first
        if (!userProfileRepository.existsById(targetUserId)) {
            throw new UserException("USER_NOT_FOUND", "User profile not found.", HttpStatus.NOT_FOUND);
        }
        return usageBoundaryService.getUsageForUser(targetUserId);
    }
}
