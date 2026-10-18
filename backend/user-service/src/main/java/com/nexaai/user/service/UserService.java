package com.nexaai.user.service;

import com.nexaai.user.domain.entity.UserPreferences;
import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.repository.UserProfileRepository;
import com.nexaai.user.dto.request.UpdatePreferencesRequest;
import com.nexaai.user.dto.request.UpdateProfileRequest;
import com.nexaai.user.dto.response.UserProfileResponse;
import com.nexaai.user.exception.UserException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserEventPublisher eventPublisher;

    public UserService(UserProfileRepository userProfileRepository,
                       UserEventPublisher eventPublisher) {
        this.userProfileRepository = userProfileRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getOwnProfile(UUID userId) {
        UserProfile user = findUserByIdOrThrow(userId);
        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateOwnProfile(UUID userId, UpdateProfileRequest req) {
        UserProfile user = findUserByIdOrThrow(userId);

        boolean changed = false;
        if (req.displayName() != null) {
            user.setDisplayName(req.displayName().trim());
            changed = true;
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl().trim());
            changed = true;
        }

        if (changed) {
            user.setUpdatedAt(Instant.now());
            user = userProfileRepository.save(user);
            eventPublisher.publishProfileUpdated(userId.toString(), Map.of(
                    "userId", userId.toString(),
                    "displayName", user.getDisplayName() != null ? user.getDisplayName() : "",
                    "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "",
                    "updatedAt", user.getUpdatedAt().toString()
            ));
        }

        return mapToResponse(user);
    }

    @Transactional
    public UserProfileResponse updateOwnPreferences(UUID userId, UpdatePreferencesRequest req) {
        UserProfile user = findUserByIdOrThrow(userId);

        UserPreferences prefs = user.getPreferences();
        if (prefs == null) {
            prefs = new UserPreferences(user);
            user.setPreferences(prefs);
        }

        if (req.theme() != null) {
            prefs.setTheme(req.theme());
        }
        if (req.defaultModel() != null) {
            prefs.setDefaultModel(req.defaultModel());
        }
        if (req.ragEnabledByDefault() != null) {
            prefs.setRagEnabledByDefault(req.ragEnabledByDefault());
        }
        if (req.streamResponses() != null) {
            prefs.setStreamResponses(req.streamResponses());
        }

        prefs.setUpdatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        user = userProfileRepository.save(user);
        eventPublisher.publishProfileUpdated(userId.toString(), Map.of(
                "userId", userId.toString(),
                "preferencesUpdated", true,
                "updatedAt", user.getUpdatedAt().toString()
        ));

        return mapToResponse(user);
    }

    @Transactional
    public void deleteOwnAccount(UUID userId) {
        UserProfile user = findUserByIdOrThrow(userId);
        userProfileRepository.delete(user);
        eventPublisher.publishAccountDeleted(userId.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOwnAccountStatus(UUID userId) {
        UserProfile user = findUserByIdOrThrow(userId);
        return Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "accountStatus", user.getAccountStatus().name(),
                "createdAt", user.getCreatedAt().toString(),
                "updatedAt", user.getUpdatedAt().toString()
        );
    }

    private UserProfile findUserByIdOrThrow(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new UserException("USER_NOT_FOUND", "User profile not found.", HttpStatus.NOT_FOUND));
    }

    public static UserProfileResponse mapToResponse(UserProfile user) {
        UserPreferences prefs = user.getPreferences();
        UserProfileResponse.PreferencesDto prefsDto;
        if (prefs != null) {
            prefsDto = new UserProfileResponse.PreferencesDto(
                    prefs.getTheme(),
                    prefs.getDefaultModel(),
                    prefs.isRagEnabledByDefault(),
                    prefs.isStreamResponses()
            );
        } else {
            prefsDto = new UserProfileResponse.PreferencesDto("system", null, true, true);
        }

        return new UserProfileResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getAccountStatus().name(),
                prefsDto,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
