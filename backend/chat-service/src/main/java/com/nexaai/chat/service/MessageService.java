package com.nexaai.chat.service;

import com.nexaai.chat.client.ChatGenerationPort;
import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.ChatConversation;
import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.domain.MessageFeedback;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.repository.ChatMessageRepository;
import com.nexaai.chat.repository.MessageFeedbackRepository;
import com.nexaai.chat.web.dto.MessageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Messages: storing turns, and the foundations regenerate and edit are built on.
 *
 * <p><strong>History is append-only.</strong> Editing a message writes a new row and supersedes
 * the old one; regenerating does the same in reverse. Nothing is ever overwritten. That is what
 * makes "show me the previous answer" possible, which is the entire reason those two features
 * exist.
 *
 * <p><strong>Every method takes the owning auth user id</strong>, and the conversation is
 * re-verified as owned before any write. Verifying only the message would be enough for a
 * well-formed row, but the conversation check is what makes a mismatched pair impossible rather
 * than merely unlikely.
 */
@Service
@Transactional
public class MessageService {

    private final ChatMessageRepository messages;
    private final MessageFeedbackRepository feedback;
    private final ConversationService conversations;
    private final ChatGenerationPort generation;
    private final ChatProperties properties;

    public MessageService(ChatMessageRepository messages, MessageFeedbackRepository feedback,
                          ConversationService conversations, ChatGenerationPort generation,
                          ChatProperties properties) {
        this.messages = messages;
        this.feedback = feedback;
        this.conversations = conversations;
        this.generation = generation;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * A conversation's current history, oldest first, with the caller's feedback attached.
     *
     * <p>Ordered by sequence number, not by timestamp: two messages written in one transaction
     * share a timestamp, and history order is not negotiable.
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> history(UUID ownerAuthUserId, UUID conversationId) {
        conversations.getOwned(ownerAuthUserId, conversationId);
        List<ChatMessage> rows = messages.findHistory(conversationId, ownerAuthUserId);
        return rows.stream().map(m -> MessageResponse.from(m, feedbackFor(ownerAuthUserId, m.getId()))).toList();
    }

    /** One of the caller's messages. */
    @Transactional(readOnly = true)
    public ChatMessage getOwnedMessage(UUID ownerAuthUserId, UUID messageId) {
        return messages.findByIdAndOwnerAuthUserId(messageId, ownerAuthUserId)
                .orElseThrow(() -> new com.nexaai.chat.exception.ChatResourceNotFoundException(
                        com.nexaai.chat.exception.ChatResourceNotFoundException.Kind.MESSAGE,
                        messageId));
    }

    // ------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------

    /**
     * Stores a user message and creates the AI response placeholder.
     *
     * <p>The placeholder row is written <em>before</em> generation is attempted. If generation is
     * unavailable, as it is in this phase, the placeholder stays PENDING and the caller gets both
     * rows back. That is the honest representation: a reply is expected and has not arrived. It
     * is not an error, because nothing failed; nothing was attempted.
     *
     * @return the user message and the placeholder that was created for it
     */
    public SendResult send(UUID ownerAuthUserId, UUID conversationId, String content,
                           String modelOverride) {
        ChatConversation conversation =
                conversations.getOwnedForUpdate(ownerAuthUserId, conversationId);
        requireNotArchived(conversation, "send a message to");

        String text = requireContent(content);
        int max = properties.getLimits().getMaxMessageLength();
        if (text.length() > max) {
            throw new IllegalArgumentException(
                    "content must be at most " + max + " characters, was " + text.length() + ".");
        }

        long sequence = messages.maxSequenceNo(conversationId) + 1;
        String model = resolveModel(conversation, modelOverride);

        ChatMessage userMessage = ChatMessage.userMessage(conversationId, ownerAuthUserId,
                sequence, text);
        messages.save(userMessage);

        // Titled from the first message only, and only while untitled, so a later message cannot
        // overwrite a title the user chose.
        conversation.applyDerivedTitleIfUntitled(text);

        long assistantSequence = sequence + 1;
        ChatMessage placeholder = ChatMessage.assistantPlaceholder(conversationId,
                ownerAuthUserId, assistantSequence, model);
        messages.save(placeholder);

        conversation.recordMessageAdded(2, java.time.Instant.now());
        conversations.save(conversation);

        attemptGeneration(conversation, userMessage, placeholder, model);

        return new SendResult(
                MessageResponse.from(userMessage),
                MessageResponse.from(placeholder));
    }

    // ------------------------------------------------------------------
    // Regenerate
    // ------------------------------------------------------------------

    /**
     * Creates a fresh assistant attempt for an existing user message.
     *
     * <p>The previous attempt is superseded rather than deleted, so the alternatives remain
     * inspectable. With generation unavailable the new attempt is PENDING, exactly like a send.
     */
    public MessageResponse regenerate(UUID ownerAuthUserId, UUID messageId) {
        ChatMessage target = getOwnedMessage(ownerAuthUserId, messageId);
        if (target.getRole() != MessageRole.ASSISTANT) {
            throw new IllegalArgumentException(
                    "Only an assistant message can be regenerated; this one is "
                            + target.getRole() + ".");
        }

        ChatConversation conversation = conversations.getOwnedForUpdate(ownerAuthUserId,
                target.getConversationId());
        requireNotArchived(conversation, "regenerate in");

        // Supersede this attempt and any later one, so the conversation shows exactly one
        // current answer per question.
        supersedeCurrentAssistantMessages(conversation.getId(), ownerAuthUserId);

        long sequence = messages.maxSequenceNo(conversation.getId()) + 1;
        String model = resolveModel(conversation, null);

        ChatMessage replacement = ChatMessage.regeneratedAssistant(conversation.getId(),
                ownerAuthUserId, sequence, model, target.getId());
        messages.save(replacement);

        conversation.recordMessageAdded(1, java.time.Instant.now());
        conversations.save(conversation);

        ChatMessage userMessage = latestUserMessageBefore(conversation.getId(), ownerAuthUserId,
                replacement.getSequenceNo());
        attemptGeneration(conversation, userMessage, replacement, model);

        return MessageResponse.from(replacement);
    }

    // ------------------------------------------------------------------
    // Edit and resend
    // ------------------------------------------------------------------

    /**
     * Replaces a user message with edited text and re-asks.
     *
     * <p>The original is superseded and kept. A new user message and a new assistant placeholder
     * follow it, so the conversation reads as a straight line while the discarded branch remains
     * queryable.
     */
    public EditResult editAndResend(UUID ownerAuthUserId, UUID messageId, String newContent) {
        ChatMessage original = getOwnedMessage(ownerAuthUserId, messageId);
        if (original.getRole() != MessageRole.USER) {
            throw new IllegalArgumentException(
                    "Only a user message can be edited; this one is " + original.getRole() + ".");
        }
        if (original.getStatus() == com.nexaai.chat.domain.MessageStatus.PENDING) {
            throw new IllegalArgumentException(
                    "This message has not been delivered yet and cannot be edited.");
        }

        ChatConversation conversation = conversations.getOwnedForUpdate(ownerAuthUserId,
                original.getConversationId());
        requireNotArchived(conversation, "edit in");

        String text = requireContent(newContent);
        int max = properties.getLimits().getMaxMessageLength();
        if (text.length() > max) {
            throw new IllegalArgumentException(
                    "content must be at most " + max + " characters, was " + text.length() + ".");
        }

        // The edited message and everything after it is the discarded branch.
        original.supersede();
        messages.save(original);
        supersedeAfter(conversation.getId(), ownerAuthUserId, original.getSequenceNo());

        long sequence = messages.maxSequenceNo(conversation.getId()) + 1;
        String model = resolveModel(conversation, null);

        ChatMessage edited = ChatMessage.editedUserMessage(conversation.getId(), ownerAuthUserId,
                sequence, text, original.getId());
        messages.save(edited);

        ChatMessage placeholder = ChatMessage.assistantPlaceholder(conversation.getId(),
                ownerAuthUserId, sequence + 1, model);
        messages.save(placeholder);

        conversation.recordMessageAdded(2, java.time.Instant.now());
        conversations.save(conversation);

        attemptGeneration(conversation, edited, placeholder, model);

        return new EditResult(MessageResponse.from(edited),
                MessageResponse.from(placeholder));
    }

    // ------------------------------------------------------------------
    // Deleting a message
    // ------------------------------------------------------------------

    /**
     * Deletes one of the caller's messages.
     *
     * <p>Hard-deletes the row. Message-level delete differs from conversation-level delete
     * deliberately: a conversation is the user's history and is soft-deleted so references
     * elsewhere keep resolving, whereas a single retracted message has nothing referencing it
     * that should outlive the retraction.
     */
    public void deleteMessage(UUID ownerAuthUserId, UUID messageId) {
        ChatMessage message = getOwnedMessage(ownerAuthUserId, messageId);
        UUID conversationId = message.getConversationId();

        messages.delete(message);

        ChatConversation conversation = conversations.getOwned(ownerAuthUserId, conversationId);
        int current = (int) messages.countCurrent(conversationId);
        conversation.recordMessageAdded(current - conversation.getMessageCount(),
                java.time.Instant.now());
        conversations.save(conversation);
    }

    // ------------------------------------------------------------------
    /**
     * The current history of a conversation as entities, oldest first.
     *
     * <p>Used by the export. Deliberately separate from {@code history}, which attaches the
     * caller's feedback and is shaped for the UI: an export should carry the conversation, not
     * a rendering of it.
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> currentHistory(UUID ownerAuthUserId, UUID conversationId) {
        conversations.getOwned(ownerAuthUserId, conversationId);
        return messages.findHistory(conversationId, ownerAuthUserId);
    }

    // Internals
    // ------------------------------------------------------------------

    /**
     * Attempts generation, completing or failing the placeholder.
     *
     * <p>When generation is unavailable the placeholder is left PENDING, untouched. It is
     * deliberately not marked FAILED: nothing failed, nothing was attempted, and a FAILED badge on
     * every reply in this phase would be a lie.
     */
    private void attemptGeneration(ChatConversation conversation, ChatMessage userMessage,
                                   ChatMessage placeholder, String model) {
        if (!generation.isAvailable()) {
            return;
        }

        ChatGenerationPort.GenerationOutcome outcome = generation.generate(
                new ChatGenerationPort.GenerationRequest(
                        conversation.getId(),
                        userMessage == null ? null : userMessage.getId(),
                        placeholder.getId(),
                        userMessage == null ? "" : userMessage.getContent(),
                        model));

        if (outcome.success()) {
            placeholder.completeWith(outcome.content(), outcome.model(),
                    outcome.inputTokens(), outcome.outputTokens());
        } else {
            placeholder.fail(outcome.failureReason());
        }
        messages.save(placeholder);
    }

    /**
     * Supersedes every current assistant message in a conversation.
     *
     * <p>Used by regenerate. Superseding all of them rather than only the named one keeps exactly
     * one current answer per question even if the caller regenerates a stale id.
     */
    private void supersedeCurrentAssistantMessages(UUID conversationId, UUID ownerAuthUserId) {
        List<ChatMessage> assistants = messages
                .findByConversationIdAndOwnerAuthUserIdAndRoleOrderBySequenceNoAsc(
                        conversationId, ownerAuthUserId, MessageRole.ASSISTANT);
        for (ChatMessage assistant : assistants) {
            if (assistant.isCurrent()) {
                assistant.supersede();
                messages.save(assistant);
            }
        }
    }

    /** Supersedes everything after a sequence number: the branch an edit discards. */
    private void supersedeAfter(UUID conversationId, UUID ownerAuthUserId, long sequenceNo) {
        for (ChatMessage message
                : messages.findHistory(conversationId, ownerAuthUserId)) {
            if (message.getSequenceNo() > sequenceNo && message.isCurrent()) {
                message.supersede();
                messages.save(message);
            }
        }
    }

    /** The most recent user message before a sequence number: the question being answered. */
    private ChatMessage latestUserMessageBefore(UUID conversationId, UUID ownerAuthUserId,
                                                long beforeSequence) {
        List<ChatMessage> users = messages
                .findByConversationIdAndOwnerAuthUserIdAndRoleOrderBySequenceNoAsc(
                        conversationId, ownerAuthUserId, MessageRole.USER);
        ChatMessage found = null;
        for (ChatMessage message : users) {
            if (message.getSequenceNo() < beforeSequence && message.isCurrent()) {
                found = message;
            }
        }
        return found;
    }

    /**
     * The caller's feedback on a message, or null when they have not rated it.
     *
     * <p>Scoped to the caller rather than looked up by message id alone. The ownership predicate
     * belongs in the query, so a code path that forgot it cannot return someone else's opinion.
     */
    private MessageFeedback feedbackFor(UUID ownerAuthUserId, UUID messageId) {
        return feedback.findByMessageIdAndOwnerAuthUserId(messageId, ownerAuthUserId)
                .orElse(null);
    }

    private static void requireNotArchived(ChatConversation conversation, String what) {
        if (conversation.getStatus().isArchived()) {
            throw new IllegalStateException(
                    "Cannot " + what + " an archived conversation. Restore it first.");
        }
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required and must not be blank.");
        }
        return content.trim();
    }

    /**
     * The model to use: this turn's override, else the conversation's pin, else the configured
     * default.
     *
     * <p>Never null. Generation needs a concrete model, and resolving it here means the
     * placeholder records what was actually asked for even before generation runs.
     */
    private String resolveModel(ChatConversation conversation, String modelOverride) {
        if (modelOverride != null && !modelOverride.isBlank()) {
            return modelOverride.trim();
        }
        if (conversation.getModel() != null && !conversation.getModel().isBlank()) {
            return conversation.getModel();
        }
        return properties.getGeneration().getDefaultModel();
    }

    /** What a send produced. */
    public record SendResult(MessageResponse userMessage, MessageResponse assistantMessage) {
    }

    /** What an edit produced. */
    public record EditResult(MessageResponse userMessage, MessageResponse assistantMessage) {
    }

}
