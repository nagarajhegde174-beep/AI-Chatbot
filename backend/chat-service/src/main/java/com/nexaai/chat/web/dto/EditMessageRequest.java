package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An edit that resends.
 *
 * <p>The new text becomes a <em>new message</em>, not an update of the old one. The original is
 * kept and marked superseded, so the edit history stays inspectable and a later regenerate can
 * still find what was asked before.
 *
 * <p>Overwriting in place would be simpler and would destroy the only record of what was
 * originally sent, which is the entire reason the edit feature exists.
 */
public record EditMessageRequest(
        @NotBlank(message = "content is required and must not be blank")
        @Size(max = 32_000, message = "content must be at most 32000 characters")
        String content) {
}