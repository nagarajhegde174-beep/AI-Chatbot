package com.nexaai.chat.web.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * A page of results.
 *
 * <p>An explicit shape rather than Spring's serialised {@code Page}, which has changed between
 * versions and warns about it at runtime. These fields are stable and the frontend has something
 * to code against.
 *
 * @param page       zero-based page index, as sent in the request
 * @param size       page size actually applied, after the server's cap
 * @param totalItems total matching rows, ignoring pagination
 * @param totalPages number of pages at this size
 */
public record PageResponse<T>(
        int page,
        int size,
        long totalItems,
        int totalPages,
        List<T> content) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(mapper).toList());
    }
}