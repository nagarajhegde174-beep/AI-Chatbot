package com.nexaai.chat.security;

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;

/**
 * The current request's raw access token, for forwarding to a downstream service.
 *
 * <p><strong>Why this exists rather than a field on {@link AuthenticatedCaller}.</strong>
 * {@code AuthenticatedCaller.getCredentials()} deliberately returns null, with the reason written
 * down: the principal must not be able to replay its own credential. Putting the raw token on the
 * authentication would undo that — anything holding the {@code Authentication} could read it, and
 * the authentication travels through the security context, the audit trail and log statements.
 *
 * <p>So the token lives here instead, in request scope, and only because Chat Service has to relay
 * it: AI Service is called on the user's behalf with the user's own token, so the request that
 * reaches a model is attributable to the person who wrote it rather than to Chat Service asking
 * anonymously.
 *
 * <p><strong>Scoped narrowly on purpose.</strong> One request, one token, gone when the request
 * ends. It is never stored in a field on a singleton, never cached, never logged, and never
 * written to a message row. The only reader is the outbound client.
 *
 * <p>Returns null outside a request — on a scheduled task or a startup hook — which callers must
 * handle. A token that appears out of nowhere would be a credential nobody scoped.
 */
@Component
/*
 * The scope NAME, not RequestAttributes.SCOPE_REQUEST.
 *
 * <p>SCOPE_REQUEST is the integer constant for the getAttribute(name, scope) overloads and is
 * trivially easy to reach for by mistake; @Scope takes the registered scope's name, which is
 * REFERENCE_REQUEST. Passing 0 to @Scope does not fail loudly -- it registers a scope called
 * "0" and the bean becomes effectively a singleton, so the token would quietly outlive its
 * request and be shared by every request on the thread.
 */
@Scope(value = RequestAttributes.REFERENCE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CallTokenHolder {

    private final ThreadLocal<String> token = new ThreadLocal<>();

    /** Records the token for the current request only. */
    public void set(String accessToken) {
        token.set(accessToken);
    }

    /** The current request's token, or null when there is none or no request. */
    public String get() {
        return token.get();
    }

    /** Drops the token as soon as it is no longer needed. */
    public void clear() {
        token.remove();
    }
}