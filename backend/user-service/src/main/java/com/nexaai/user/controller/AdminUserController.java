package com.nexaai.user.controller;

import com.nexaai.user.domain.model.AccountStatus;
import com.nexaai.user.dto.request.UpdateUserStatusRequest;
import com.nexaai.user.dto.response.PageResponse;
import com.nexaai.user.dto.response.SubscriptionInfoResponse;
import com.nexaai.user.dto.response.UsageInfoResponse;
import com.nexaai.user.dto.response.UserProfileResponse;
import com.nexaai.user.dto.response.UserSummaryResponse;
import com.nexaai.user.security.AuthenticatedUser;
import com.nexaai.user.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin Users", description = "Admin user-management endpoints")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "List, search, and filter users", description = "Paginated list with optional search query and account status filter.")
    public ResponseEntity<PageResponse<UserSummaryResponse>> listUsers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AccountStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminUserService.listUsers(query, status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "View user details", description = "Returns full profile details of a single user without exposing sensitive credentials.")
    public ResponseEntity<UserProfileResponse> getUserDetails(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getUserDetails(id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update user status", description = "Activate, deactivate, or suspend a user account.")
    public ResponseEntity<UserProfileResponse> updateUserStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal AuthenticatedUser adminUser) {
        return ResponseEntity.ok(adminUserService.updateUserStatus(adminUser.userId(), id, request));
    }

    @GetMapping("/{id}/subscription")
    @Operation(summary = "View user subscription info", description = "Retrieves subscription tier and status across service boundaries.")
    public ResponseEntity<SubscriptionInfoResponse> getUserSubscription(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getUserSubscription(id));
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "View user usage info", description = "Retrieves token, message, and storage usage across service boundaries.")
    public ResponseEntity<UsageInfoResponse> getUserUsage(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.getUserUsage(id));
    }
}
