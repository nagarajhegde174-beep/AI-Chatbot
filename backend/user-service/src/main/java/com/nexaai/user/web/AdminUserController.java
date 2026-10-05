package com.nexaai.user.web;

import com.nexaai.user.client.SubscriptionLookup;
import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.security.CurrentCaller;
import com.nexaai.user.service.AdminUserService;
import com.nexaai.user.web.dto.AdminUserDetail;
import com.nexaai.user.web.dto.AdminUserSummary;
import com.nexaai.user.web.dto.PageResponse;
import com.nexaai.user.web.dto.PreferenceResponse;
import com.nexaai.user.web.dto.StatusChangeRequest;
import com.nexaai.user.web.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The administrative user-management API.
 *
 * <p>{@code @PreAuthorize("hasRole('ADMIN')")} on the class. That is a method-security check,
 * evaluated after routing and independent of the URL patterns in the security filter chain, so
 * adding a route here without a role check would fail closed rather than open.
 *
 * <p>Users are addressed by <strong>auth-service id</strong>, not by the internal profile id.
 * The auth id is the platform-wide identity; the surrogate id is this service's own numbering
 * and means nothing outside it.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Users",
        description = "Administrative user management. Requires the ADMIN role.")
public class AdminUserController {

    private final AdminUserService adminUsers;
    private final SubscriptionLookup subscriptions;

    public AdminUserController(AdminUserService adminUsers, SubscriptionLookup subscriptions) {
        this.adminUsers = adminUsers;
        this.subscriptions = subscriptions;
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "List users",
            description = "Filter by status and role, search by display name or email, and page "
                    + "through the result. The page size is capped server-side.")
    public PageResponse<AdminUserSummary> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<UserProfile> result = adminUsers.list(
                parseStatus(status), parseRole(role), search, page, size);

        return PageResponse.from(result, p -> new AdminUserSummary(
                p.getId(),
                p.getAuthUserId(),
                p.getEmail(),
                p.getDisplayName(),
                p.getAccountStatus(),
                p.getRole(),
                p.getStatusReason(),
                p.isEmailVerified(),
                p.getPlanName(),
                p.getCreatedAt()));
    }

    @GetMapping("/counts")
    @Operation(summary = "Count users by account status")
    public CountsResponse counts() {
        return new CountsResponse(
                adminUsers.countsByStatus().stream()
                        .map(c -> new StatusCount(c.status().name(), c.count()))
                        .toList());
    }

    /** Counts by status. */
    public record CountsResponse(java.util.List<StatusCount> byStatus) {
    }

    /** One status and its count. */
    public record StatusCount(String status, long count) {
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    @GetMapping("/{authUserId}")
    @Operation(summary = "View a user's details",
            description = "Profile, preferences and the full status history including which "
                    + "administrator made each change.")
    public AdminUserDetail detail(@PathVariable UUID authUserId) {
        UserProfile profile = adminUsers.requireByAuthUserId(authUserId);
        return AdminUserDetail.from(
                UserProfileResponse.from(profile),
                PreferenceResponse.from(profile),
                adminUsers.statusHistoryFor(authUserId));
    }

    @GetMapping("/{authUserId}/usage")
    @Operation(summary = "View a user's subscription and usage",
            description = "Fetched from Subscription Service across the service boundary. Reports "
                    + "'unavailable' rather than zero when that service cannot be reached, because "
                    + "zero would read as 'this user has used nothing'.")
    public SubscriptionLookup.UsageView usage(@PathVariable UUID authUserId) {
        // Existence is checked first so a 404 is returned for an unknown user rather than an
        // empty usage record from another service.
        adminUsers.requireByAuthUserId(authUserId);
        return subscriptions.usageFor(authUserId);
    }

    // ------------------------------------------------------------------
    // Status actions
    // ------------------------------------------------------------------

    @PostMapping("/{authUserId}/activate")
    @Operation(summary = "Activate a user")
    public UserProfileResponse activate(@PathVariable UUID authUserId) {
        return UserProfileResponse.from(
                adminUsers.activate(authUserId, CurrentCaller.authUserId()));
    }

    @PostMapping("/{authUserId}/suspend")
    @Operation(summary = "Suspend a user",
            description = "A reason is required and is shown to the account holder.")
    public UserProfileResponse suspend(@PathVariable UUID authUserId,
                                       @RequestBody(required = false) StatusChangeRequest request) {
        String reason = request == null ? null : request.reason();
        return UserProfileResponse.from(
                adminUsers.suspend(authUserId, reason, CurrentCaller.authUserId()));
    }

    @PostMapping("/{authUserId}/deactivate")
    @Operation(summary = "Deactivate a user",
            description = "Permanent. No transition out of DEACTIVATED exists.")
    public UserProfileResponse deactivate(@PathVariable UUID authUserId,
                                          @RequestBody(required = false) StatusChangeRequest request) {
        String reason = request == null ? null : request.reason();
        return UserProfileResponse.from(
                adminUsers.deactivate(authUserId, reason, CurrentCaller.authUserId()));
    }

    @PostMapping("/{authUserId}/status")
    @Operation(summary = "Set a user's status explicitly",
            description = "The general form of the three actions above. Rejects a transition the "
                    + "lifecycle forbids, including any move out of DEACTIVATED.")
    public UserProfileResponse changeStatus(@PathVariable UUID authUserId,
                                            @Valid @RequestBody StatusChangeRequest request) {
        AccountStatus target = AccountStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        return UserProfileResponse.from(adminUsers.changeStatus(
                authUserId, target, request.reason(), CurrentCaller.authUserId()));
    }

    // ------------------------------------------------------------------
    // Query parsing
    //
    // An unrecognised filter is a client error, not a reason to silently drop the filter and
    // return every user. Returning everything because a filter was misspelled is the kind of
    // response that gets cached and screenshotted.
    // ------------------------------------------------------------------

    private static AccountStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + value + "'. Expected one of: "
                    + java.util.Arrays.toString(AccountStatus.values()));
        }
    }

    private static Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown role '" + value + "'. Expected one of: "
                    + java.util.Arrays.toString(Role.values()));
        }
    }
}