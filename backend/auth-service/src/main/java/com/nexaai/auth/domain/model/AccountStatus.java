package com.nexaai.auth.domain.model;

/**
 * Account status per docs/SERVICE_CONTRACTS.md section 3.3 and section 5.
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
