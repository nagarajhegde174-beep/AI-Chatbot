package com.nexaai.user.controller;

import com.nexaai.user.dto.request.UpdatePreferencesRequest;
import com.nexaai.user.dto.request.UpdateProfileRequest;
import com.nexaai.user.dto.response.UserProfileResponse;
import com.nexaai.user.security.AuthenticatedUser;
import com.nexaai.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Profile", description = "Endpoints for managing own user profile and preferences")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get own profile", description = "Returns profile, preferences, and account status of the authenticated user.")
    public ResponseEntity<UserProfileResponse> getOwnProfile(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(userService.getOwnProfile(user.userId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update own profile", description = "Updates displayName and/or avatarUrl for the authenticated user.")
    public ResponseEntity<UserProfileResponse> updateOwnProfile(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateOwnProfile(user.userId(), request));
    }

    @PutMapping("/me/preferences")
    @Operation(summary = "Replace user preferences", description = "Replaces theme, default model, RAG default, and streaming settings.")
    public ResponseEntity<UserProfileResponse> updatePreferences(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(userService.updateOwnPreferences(user.userId(), request));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete own account", description = "Permanently deletes user profile and emits account deletion event.")
    public ResponseEntity<Map<String, String>> deleteOwnAccount(@AuthenticationPrincipal AuthenticatedUser user) {
        userService.deleteOwnAccount(user.userId());
        return ResponseEntity.ok(Map.of("message", "Account successfully deleted."));
    }

    @GetMapping("/me/status")
    @Operation(summary = "Get account status", description = "Returns active/suspended status and timestamps.")
    public ResponseEntity<Map<String, Object>> getOwnStatus(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(userService.getOwnAccountStatus(user.userId()));
    }
}
