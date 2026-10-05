package com.nexaai.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller.
 *
 * <p>Held in memory for the duration of the request. It carries the <strong>auth-service user
 * id</strong>, not the profile id, because that is the identity the token attests to. The
 * profile is looked up by that id.
 */
public class AuthenticatedCaller extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final UUID authUserId;
    private final String email;
    private final boolean admin;

    public AuthenticatedCaller(UUID authUserId, String email, boolean admin, List<String> roles) {
        super(toAuthorities(roles));
        this.authUserId = authUserId;
        this.email = email;
        this.admin = admin;
        setAuthenticated(true);
    }

    private static List<SimpleGrantedAuthority> toAuthorities(List<String> roles) {
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return authorities;
    }

    @Override
    public Object getCredentials() {
        // Never the raw token: the principal must not be able to replay its own credential.
        return null;
    }

    /**
     * The stable auth user id, not {@code this}.
     *
     * <p>Returning {@code this} looks harmless and is not: {@code AbstractAuthenticationToken
     * .getName()} checks whether the principal is an {@code AuthenticatedPrincipal} and, if so,
     * calls {@code getName()} on it. A principal that is itself the authentication token
     * therefore recurses until the stack overflows. The id is also the right value to hand to
     * audit logs, where a name is what gets written.
     */
    @Override
    public Object getPrincipal() {
        return this.authUserId;
    }

    /**
     * Overridden explicitly for the same reason as {@link #getPrincipal()}: without it the
     * inherited implementation delegates to itself and overflows the stack. Returns the
     * auth-service user id, deliberately not the email address, because this value is written
     * to logs.
     */
    @Override
    public String getName() {
        return this.authUserId.toString();
    }

    /** The stable auth-service user id. The key every lookup uses. */
    public UUID authUserId() {
        return authUserId;
    }

    public String email() {
        return email;
    }

    public boolean isAdmin() {
        return admin;
    }

    /** Thrown when a token is unusable, so the filter can authenticate nothing rather than throw. */
    public static class InvalidToken extends RuntimeException {
        private InvalidToken(JwtException cause) {
            super(cause.getMessage(), cause);
        }
    }
}