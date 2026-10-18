package com.nexaai.user.dto.response;

import java.util.List;

/**
 * Standard paginated envelope per SERVICE_CONTRACTS.md §1.5.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {}
