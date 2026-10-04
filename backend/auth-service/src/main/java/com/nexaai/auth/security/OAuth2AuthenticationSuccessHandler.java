package com.nexaai.auth.security;

import com.nexaai.auth.domain.TokenSource;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.service.GoogleOAuthService;
import com.nexaai.auth.web.ClientContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Completes a Google sign-in.
 *
 * <p>Issues NexaAI's own tokens rather than accepting Google's. Every service in the system
 * validates one token format; forwarding a provider token would mean each service needing a
 * Google client and a network call, and would make the provider's availability part of every
 * authenticated request.
 *
 * <p><strong>Callback handling.</strong> The handler never trusts a URL from the request. The
 * post-login redirect is read from configuration, so a forged {@code state} or {@code redirect_uri}
 * cannot bounce a user to an attacker's site with a token in the fragment.
 */
@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final GoogleOAuthService googleOAuthService;
    private final TokenCookieService cookieService;

    public OAuth2AuthenticationSuccessHandler(GoogleOAuthService googleOAuthService,
                                              TokenCookieService cookieService) {
        this.googleOAuthService = googleOAuthService;
        this.cookieService = cookieService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unsupported authentication type.");
            return;
        }

        ClientContext client = ClientContext.from(request);

        try {
            AuthService.AuthenticationResult result = googleOAuthService.authenticate(
                    oauth2Token, client.ipAddress(), client.userAgent(), client.correlationId());

            cookieService.writeAccessTokenCookie(response,
                    result.accessToken().token(), result.accessToken().expiresAt());
            cookieService.writeRefreshTokenCookie(response,
                    result.refreshToken().token(), result.refreshToken().entity().getExpiresAt());

            // No token in the URL. A redirect with a token in a fragment ends up in browser
            // history and in Referer headers.
            String target = UriComponentsBuilder
                    .fromUriString(frontendBaseUrl(request))
                    .path("/auth/callback")
                    .queryParam("status", "ok")
                    .build()
                    .toUriString();
            response.sendRedirect(target);

        } catch (RuntimeException e) {
            log.warn("Google sign-in was refused: {}", e.getMessage());
            String target = UriComponentsBuilder
                    .fromUriString(frontendBaseUrl(request))
                    .path("/auth/callback")
                    .queryParam("status", "error")
                    .queryParam("reason", "not_permitted")
                    .build()
                    .toUriString();
            response.sendRedirect(target);
        }
    }

    /**
     * The frontend origin, from configuration.
     *
     * <p>Never from the request. A redirect target derived from a request parameter is an open
     * redirect, and an open redirect on a login callback is how a phishing page harvests
     * credentials.
     */
    private String frontendBaseUrl(HttpServletRequest request) {
        String configured = request.getServletContext().getInitParameter("nexa.frontend-base-url");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return System.getenv().getOrDefault("NEXA_AUTH_FRONTEND_BASE_URL", "http://localhost:5173");
    }
}