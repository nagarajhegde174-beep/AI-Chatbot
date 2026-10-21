package com.nexaai.chat;

import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.domain.MessageStatus;
import com.nexaai.chat.repository.ConversationRepository;
import com.nexaai.chat.repository.MessageRepository;
import com.nexaai.chat.service.AiSummarizationClient;
import com.nexaai.chat.service.ContextWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 5 conversation memory.
 *
 * <p>Tests covered:
 * <ol>
 *   <li>Context retrieval — returns recent messages in order</li>
 *   <li>Context isolation — different conversations never bleed</li>
 *   <li>Long conversation — token budget trims excess messages</li>
 *   <li>Summarization trigger — fires when threshold exceeded</li>
 *   <li>Summary prepended — context starts with SYSTEM summary message</li>
 *   <li>Clearing memory — deletes messages and resets summary</li>
 *   <li>Summarization failure (model error) — graceful degradation</li>
 *   <li>Token estimation — heuristic accuracy</li>
 *   <li>Empty conversation — returns empty context</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ContextWindowServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private AiSummarizationClient summarizationClient;

    private ContextWindowService service;

    private static final int MAX_CONTEXT_TOKENS = 500;  // small budget for testing
    private static final int RECENT_WINDOW_SIZE = 3;    // 3 turns = 6 messages
    private static final int SUMMARIZATION_THRESHOLD = 10;

    @BeforeEach
    void setUp() {
        service = new ContextWindowService(messageRepository, conversationRepository, summarizationClient);
        ReflectionTestUtils.setField(service, "maxContextTokens", MAX_CONTEXT_TOKENS);
        ReflectionTestUtils.setField(service, "recentWindowSize", RECENT_WINDOW_SIZE);
        ReflectionTestUtils.setField(service, "summarizationThreshold", SUMMARIZATION_THRESHOLD);
    }

    // -----------------------------------------------------------------------
    // 1. Context retrieval — returns recent messages in chronological order
    // -----------------------------------------------------------------------

    @Test
    void buildContext_returnsRecentMessagesInChronologicalOrder() {
        Conversation conv = conversation();
        conv.setSummaryMessageSeq(0);

        List<Message> recent = List.of(
                message(conv, MessageRole.USER,      "Hello",    1),
                message(conv, MessageRole.ASSISTANT, "Hi there", 2),
                message(conv, MessageRole.USER,      "How?",     3)
        );
        // stub "recent N messages" — return reversed so service reverses back
        List<Message> reversed = new ArrayList<>(recent);
        Collections.reverse(reversed);
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(reversed);

        // stub "unsummarized" — below threshold
        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 0))
                .thenReturn(recent);

        List<ContextWindowService.ContextMessage> ctx = service.buildContext(conv);

        assertThat(ctx).hasSize(3);
        assertThat(ctx.get(0).role()).isEqualTo("USER");
        assertThat(ctx.get(0).content()).isEqualTo("Hello");
        assertThat(ctx.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(ctx.get(2).role()).isEqualTo("USER");
    }

    // -----------------------------------------------------------------------
    // 2. Context isolation — conversations are scoped by ID
    // -----------------------------------------------------------------------

    @Test
    void buildContext_usesConversationScopedQuery() {
        Conversation convA = conversation();
        Conversation convB = conversation(); // different ID

        when(messageRepository.findByConversationIdAndSequenceNumberAfter(any(), eq(0)))
                .thenReturn(Collections.emptyList());
        when(messageRepository.findRecentByConversationId(any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        service.buildContext(convA);
        service.buildContext(convB);

        // Verify each call used its own conversation ID
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(messageRepository, times(2))
                .findRecentByConversationId(captor.capture(), any(Pageable.class));
        List<UUID> capturedIds = captor.getAllValues();
        assertThat(capturedIds).containsExactlyInAnyOrder(convA.getId(), convB.getId());
        assertThat(capturedIds.get(0)).isNotEqualTo(capturedIds.get(1));
    }

    // -----------------------------------------------------------------------
    // 3. Long conversation — token budget trims oldest messages
    // -----------------------------------------------------------------------

    @Test
    void buildContext_trimsToTokenBudget() {
        Conversation conv = conversation();
        conv.setSummaryMessageSeq(0);

        // Create messages that together exceed MAX_CONTEXT_TOKENS
        // Each message is 120 chars ≈ 30 tokens; 6 × 30 = 180 — still within 500
        // Make each message huge: 600 chars ≈ 150 tokens; 6 × 150 = 900 > 500
        String bigContent = "A".repeat(600); // 150 tokens each
        List<Message> recent = List.of(
                message(conv, MessageRole.USER,      bigContent, 1),
                message(conv, MessageRole.ASSISTANT, bigContent, 2),
                message(conv, MessageRole.USER,      bigContent, 3),
                message(conv, MessageRole.ASSISTANT, bigContent, 4),
                message(conv, MessageRole.USER,      bigContent, 5),
                message(conv, MessageRole.ASSISTANT, bigContent, 6)
        );
        List<Message> reversed = new ArrayList<>(recent);
        Collections.reverse(reversed);

        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 0))
                .thenReturn(recent);
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(reversed);

        List<ContextWindowService.ContextMessage> ctx = service.buildContext(conv);

        // Total tokens should not exceed budget
        int total = ctx.stream()
                .mapToInt(m -> service.estimateTokens(m.content()))
                .sum();
        assertThat(total).isLessThanOrEqualTo(MAX_CONTEXT_TOKENS);
        // At least some context remains
        assertThat(ctx).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // 4. Summarization trigger — fires when threshold exceeded
    // -----------------------------------------------------------------------

    @Test
    void buildContext_triggersSummarizationWhenThresholdExceeded() {
        Conversation conv = conversation();
        conv.setSummaryMessageSeq(0);

        // Create SUMMARIZATION_THRESHOLD + 5 messages (exceeds threshold)
        int total = SUMMARIZATION_THRESHOLD + 5;
        List<Message> messages = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            messages.add(message(conv, i % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER,
                    "msg " + i, i));
        }

        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 0))
                .thenReturn(messages);
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(messages.subList(0, Math.min(RECENT_WINDOW_SIZE * 2, messages.size())));
        when(summarizationClient.summarize(any(), anyList(), any()))
                .thenReturn("Summarized context.");
        when(conversationRepository.save(any())).thenReturn(conv);

        service.buildContext(conv);

        verify(summarizationClient, times(1)).summarize(any(), anyList(), any());
        verify(conversationRepository, atLeastOnce()).save(conv);
        assertThat(conv.getContextSummary()).isEqualTo("Summarized context.");
    }

    // -----------------------------------------------------------------------
    // 5. Summary prepended — SYSTEM message appears first
    // -----------------------------------------------------------------------

    @Test
    void buildContext_prependsSummaryAsSystemMessage() {
        Conversation conv = conversation();
        conv.setContextSummary("User is debugging a Java service.");
        conv.setSummaryMessageSeq(5);

        // Below threshold so no new summarization
        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 5))
                .thenReturn(Collections.emptyList());
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<ContextWindowService.ContextMessage> ctx = service.buildContext(conv);

        assertThat(ctx).hasSize(1);
        assertThat(ctx.get(0).role()).isEqualTo("SYSTEM");
        assertThat(ctx.get(0).content()).contains("User is debugging a Java service.");
    }

    // -----------------------------------------------------------------------
    // 6. Clear memory — messages deleted, summary reset
    // -----------------------------------------------------------------------

    @Test
    void clearMemory_deletesMessagesAndResetsSummary() {
        Conversation conv = conversation();
        conv.setContextSummary("Some old summary.");
        conv.setSummaryMessageSeq(42);

        when(conversationRepository.save(any())).thenReturn(conv);

        service.clearMemory(conv);

        verify(messageRepository, times(1)).deleteByConversationId(conv.getId());
        verify(conversationRepository, times(1)).save(conv);
        assertThat(conv.getContextSummary()).isNull();
        assertThat(conv.getSummaryMessageSeq()).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // 7. Summarization failure — graceful degradation, old summary kept
    // -----------------------------------------------------------------------

    @Test
    void buildContext_whenSummarizationFails_keepsOldSummary() {
        Conversation conv = conversation();
        conv.setContextSummary("Previous summary.");
        conv.setSummaryMessageSeq(0);

        int total = SUMMARIZATION_THRESHOLD + 5;
        List<Message> messages = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            messages.add(message(conv, MessageRole.USER, "msg " + i, i));
        }

        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 0))
                .thenReturn(messages);
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(messages.subList(0, Math.min(RECENT_WINDOW_SIZE * 2, messages.size())));

        // Summarization client returns null (simulating model failure)
        when(summarizationClient.summarize(any(), anyList(), any())).thenReturn(null);

        service.buildContext(conv);

        // Old summary must be untouched; watermark not advanced
        assertThat(conv.getContextSummary()).isEqualTo("Previous summary.");
        assertThat(conv.getSummaryMessageSeq()).isEqualTo(0);
        verify(conversationRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // 8. Token estimation heuristic
    // -----------------------------------------------------------------------

    @Test
    void estimateTokens_returnsReasonableApproximation() {
        // 4 chars ≈ 1 token
        assertThat(service.estimateTokens("test")).isEqualTo(1);
        assertThat(service.estimateTokens("Hello world!")).isEqualTo(3);
        assertThat(service.estimateTokens("A".repeat(400))).isEqualTo(100);
    }

    @Test
    void estimateTokens_handlesNullAndBlank() {
        assertThat(service.estimateTokens(null)).isEqualTo(0);
        assertThat(service.estimateTokens("")).isEqualTo(0);
        assertThat(service.estimateTokens("   ")).isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // 9. Empty conversation — returns empty context
    // -----------------------------------------------------------------------

    @Test
    void buildContext_emptyConversation_returnsEmpty() {
        Conversation conv = conversation();
        conv.setSummaryMessageSeq(0);

        when(messageRepository.findByConversationIdAndSequenceNumberAfter(conv.getId(), 0))
                .thenReturn(Collections.emptyList());
        when(messageRepository.findRecentByConversationId(eq(conv.getId()), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<ContextWindowService.ContextMessage> ctx = service.buildContext(conv);

        assertThat(ctx).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Conversation conversation() {
        Conversation c = new Conversation(UUID.randomUUID(), UUID.randomUUID(), "Test Conv", "gpt-4o-mini");
        return c;
    }

    private Message message(Conversation conv, MessageRole role, String content, int seq) {
        Message m = new Message(UUID.randomUUID(), conv, conv.getUserId(), role, content, seq, "gpt-4o-mini");
        m.setStatus(MessageStatus.COMPLETED);
        m.setCreatedAt(Instant.now());
        m.setUpdatedAt(Instant.now());
        return m;
    }
}
