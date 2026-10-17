package com.nexaai.auth.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.service.CookieService;
import com.nexaai.auth.service.GoogleOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleOAuthService googleOAuthService;
    private final AuthService authService;
    private final CookieService cookieService;
    private final ObjectMapper objectMapper;

    public OAuth2AuthenticationSuccessHandler(GoogleOAuthService googleOAuthService,
                                              AuthService authService,
                                              CookieService cookieService,
                                              ObjectMapper objectMapper) {
        this.googleOAuthService = googleOAuthService;
        this.authService = authService;
        this.cookieService = cookieService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2User oauth2User = oauthToken.getPrincipal();
            GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oauth2User.getAttributes());

            String correlationId = (String) request.getAttribute("X-Correlation-Id");
            AuthUser user = googleOAuthService.processGoogleUser(
                    userInfo.getId(),
                    userInfo.getEmail(),
                    userInfo.getName(),
                    userInfo.isEmailVerified(),
                    correlationId
            );

            AuthResponse authResponse = authService.issueTokens(user);

            ResponseCookie refreshCookie = cookieService.createRefreshTokenCookie(
                    authResponse.refreshToken(),
                    7 * 24 * 60 * 60
            );
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), authResponse);
        }
    }
}
