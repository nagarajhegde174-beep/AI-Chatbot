package com.nexaai.chat.exception;

/**
 * Thrown when a caller asks for something their role may not do.
 *
 * <p>Distinct from {@link ChatResourceNotFoundException} on purpose. "You may not" is about the
 * caller's role and is safe to say. "It does not exist" versus "it is not yours" is about someone
 * else's data and must not be distinguishable.
 */
public class ChatAccessDeniedException extends RuntimeException {

    public ChatAccessDeniedException(String message) {
        super(message);
    }
}