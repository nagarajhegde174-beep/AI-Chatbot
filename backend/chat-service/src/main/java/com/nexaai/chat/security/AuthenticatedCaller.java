package com.nexaai.chat.security;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated caller.
 *
 * <p>Carries the <strong>auth-service user id</strong>, because that is the identity the token
 * attests to and the key every conversation is owned by. A profile id would be a different
 * identity in a different service, and using one here would couple this service to another's
 * numbering.
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
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
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
     * The auth user id, not {@code this}.
     *
     * <p>Returning {@code this} looks harmless and is not:
     * {@code AbstractAuthenticationToken.getName()} checks whether the principal is an
     * {@code AuthenticatedPrincipal} and, if so, calls {@code getName()} on it, so a principal
     * that is itself the token recurses until the stack overflows.
     */
    @Override
    public Object getPrincipal() {
        return this.authUserId;
    }

    /** Overridden for the same reason as {@link #getPrincipal()}. Returns the id, not the email,
     *  because this value is written to logs. */
    @Override
    public String getName() {
        return this.authUserId.toString();
    }

    /** The key every ownership check uses. */
    public UUID authUserId() {
        return authUserId;
    }

    public String email() {
        return email;
    }

    public boolean isAdmin() {
        return admin;
    }
}