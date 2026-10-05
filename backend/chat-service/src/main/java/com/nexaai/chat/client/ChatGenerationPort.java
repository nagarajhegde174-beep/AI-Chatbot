package com.nexaai.chat.client;

import java.util.UUID;

/**
 * Where AI generation happens.
 *
 * <p><strong>This is an interface with no working implementation in this phase, and that is the
 * point.</strong> The AI Service arrives in a later phase. Defining the seam now means the rest of
 * Chat Service is built and tested against a real contract rather than a guess, and the phase that
 * implements generation has somewhere concrete to plug into.
 *
 * <p><strong>What a send does when nothing implements this:</strong> the user message is stored,
 * an assistant row is created with status PENDING, and it stays PENDING. Not an error, and
 * certainly not fabricated content. The user sees that a reply is expected and has not arrived,
 * which is the truth. Returning a 500 saying "the AI service is down" would be false — nothing
 * failed; nothing was attempted.
 *
 * <p><strong>Why the placeholder is written before generation is attempted:</strong> if the row
 * were created after, an interrupted stream, a killed process or a timeout would leave the user
 * with a question, no reply, and no evidence anything happened. Writing it first means the
 * conversation always shows a reply slot, and a later phase can reconcile rows stuck in PENDING.
 */
public interface ChatGenerationPort {

    /**
     * Whether generation is available.
     *
     * <p>Callers check this and leave the placeholder PENDING when it is false, rather than
     * calling and handling an exception. The distinction matters: "not available" is a state to
     * report, "failed" is an incident.
     */
    boolean isAvailable();

    /**
     * Attempts to produce an assistant reply.
     *
     * <p>Must not throw for an ordinary provider failure; a failure is returned as a
     * {@link GenerationOutcome#failed}. An exception here means the port itself is broken, which
     * is worth surfacing as a 500.
     *
     * @param request the conversation, the new user message, and the model to use
     */
    GenerationOutcome generate(GenerationRequest request);

    /**
     * What generation is being asked for.
     *
     * @param conversationId the conversation being answered
     * @param userMessageId  the user message being answered, so a stream can be correlated
     * @param placeholderId  the PENDING assistant row to complete
     * @param content        the user's new message
     * @param model          the resolved model, never null
     */
    record GenerationRequest(
            UUID conversationId,
            UUID userMessageId,
            UUID placeholderId,
            String content,
            String model) {
    }

    /**
     * The result of a generation attempt.
     *
     * @param content      the generated text, when successful
     * @param model        the model that actually produced it, which may differ from the one
     *                     requested and must be recorded as what actually ran
     * @param inputTokens  prompt tokens consumed
     * @param outputTokens completion tokens produced
     */
    record GenerationOutcome(
            boolean success,
            String content,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            String failureReason) {

        public static GenerationOutcome completed(String content, String model,
                                                  Integer inputTokens, Integer outputTokens) {
            return new GenerationOutcome(true, content, model, inputTokens, outputTokens, null);
        }

        /**
         * A failure.
         *
         * <p>The reason is short and non-sensitive by contract. An implementer must not put a
         * provider key, a full prompt or a stack trace in here: it is stored on the message and
         * returned to the user.
         */
        public static GenerationOutcome failed(String reason) {
            return new GenerationOutcome(false, null, null, null, null,
                    reason == null ? "Generation did not complete." : reason);
        }
    }
}