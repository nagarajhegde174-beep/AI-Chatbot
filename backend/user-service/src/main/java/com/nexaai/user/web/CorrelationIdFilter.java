package com.nexaai.user.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id.
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured so a trace survives across services;
 * otherwise one is generated. Either way it goes into the log via SLF4J's MDC and into the
 * response header, so a support ticket quoting a trace id can be found in the logs.
 *
 * <p>The header is taken from the request only when it looks like a UUID. An arbitrary string
 * from an untrusted caller would otherwise be written straight into the log, which is a log
 * injection route.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Request attribute and response header name. */
    public static final String HEADER = "X-Correlation-Id";

    public static final String ATTRIBUTE = "nexa.correlationId";

    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String correlationId = sanitize(request.getHeader(HEADER));

        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Cleared in a finally block. A leaked MDC entry is attached to whatever request
            // this thread serves next, which is how one user's id ends up on another's log line.
            MDC.remove(MDC_KEY);
        }
    }

    /** Accepts an inbound id only if it parses as a UUID; otherwise generates a fresh one. */
    private static String sanitize(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Not a UUID: fall through and generate one.
            }
        }
        return UUID.randomUUID().toString();
    }
}