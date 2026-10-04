package com.nexaai.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Writes and reads the token cookies.
 *
 * <p><strong>Why cookies rather than a JSON body.</strong> Tokens are delivered as
 * HTTP-only cookies so JavaScript cannot read them. A token in a JSON body is readable by
 * any script on the page, which turns a cross-site scripting bug from a nuisance into
 * credential theft. An {@code HttpOnly} cookie is invisible to script
 * ({@code docs/SECURITY.md} section 3).
 *
 * <p>The consequence, stated plainly because it is a real trade-off: cookies are sent
 * automatically, so cookie-based auth needs CSRF protection. A bearer token in an
 * {@code Authorization} header needs none, because a browser will not attach that header
 * on its own. {@code SameSite=Strict} plus a CSRF token is the mitigation, and both are
 * configured rather than assumed.
 *
 * <p>The refresh token is scoped to the refresh endpoint by path. A token that can be read
 * on every request is a token with more exposure than it needs.
 */
@Component
public class TokenCookieService {

    private final String prefix;
    private final boolean secure;
    private final String sameSite;
    private final String path;
    private final String domain;

    public TokenCookieService(com.nexaai.auth.config.AuthProperties properties) {
        com.nexaai.auth.config.AuthProperties.Cookie cookie = properties.getCookie();
        this.prefix = cookie.getPrefix();
        this.secure = cookie.isSecure();
        this.sameSite = cookie.getSameSite();
        this.path = cookie.getPath();
        this.domain = cookie.getDomain();
    }

    public String accessTokenCookieName() {
        return prefix + "_access_token";
    }

    public String refreshTokenCookieName() {
        return prefix + "_refresh_token";
    }

    /** Reads the access token from the request, falling back to the {@code Authorization} header. */
    public Optional<String> readAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String name = accessTokenCookieName();
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return Optional.of(cookie.getValue());
                }
            }
        }
        // The header fallback keeps non-browser clients (curl, the gateway, service-to-service
        // calls) working. Browsers use the cookie and never need it.
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String value = header.substring(7).trim();
            if (!value.isEmpty()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            String name = refreshTokenCookieName();
            return Arrays.stream(cookies)
                    .filter(c -> name.equals(c.getName()))
                    .map(Cookie::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst();
        }
        return Optional.empty();
    }

    /**
     * Writes the access token cookie.
     *
     * <p>Expiry is derived from the JWT's own expiry rather than the configured TTL, so the
     * cookie and the token inside it disappear together. A cookie that outlives its token
     * leaves a stale value the browser keeps sending.
     */
    public void writeAccessTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        Cookie cookie = baseCookie(accessTokenCookieName(), token);
        cookie.setMaxAge(secondsUntil(expiresAt));
        cookie.setPath(path);
        response.addCookie(cookie);
    }

    /**
     * Writes the refresh token cookie, scoped to the refresh path.
     *
     * <p>The narrow path is a deliberate reduction in exposure: the refresh token is only
     * ever needed at {@code /api/v1/auth/refresh}, so it is not attached to every other
     * request.
     */
    public void writeRefreshTokenCookie(HttpServletResponse response, String token, Instant expiresAt) {
        Cookie cookie = baseCookie(refreshTokenCookieName(), token);
        cookie.setMaxAge(secondsUntil(expiresAt));
        cookie.setPath(refreshPath());
        response.addCookie(cookie);
    }

    /** Clears both token cookies. */
    public void clearTokenCookies(HttpServletResponse response) {
        Cookie access = baseCookie(accessTokenCookieName(), "");
        access.setMaxAge(0);
        access.setPath(path);

        Cookie refresh = baseCookie(refreshTokenCookieName(), "");
        refresh.setMaxAge(0);
        refresh.setPath(refreshPath());

        response.addCookie(access);
        response.addCookie(refresh);
    }

    /** The path the refresh cookie is scoped to. Matches the refresh endpoint. */
    public String refreshPath() {
        return "/api/v1/auth/refresh";
    }

    private Cookie baseCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        // HTTP-only: unreadable by JavaScript. The core of this whole mechanism.
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setAttribute("SameSite", sameSite);
        if (domain != null && !domain.isBlank()) {
            cookie.setDomain(domain);
        }
        return cookie;
    }

    /**
     * Converts an instant to a non-negative max-age.
     *
     * <p>A negative value would produce a session cookie that outlives its token, so an
     * already-expired token is written with max-age 0 and dies immediately.
     */
    private static int secondsUntil(Instant expiresAt) {
        long seconds = Duration.between(Instant.now(), expiresAt).getSeconds();
        return Math.max(0, (int) Math.min(seconds, Integer.MAX_VALUE));
    }
}