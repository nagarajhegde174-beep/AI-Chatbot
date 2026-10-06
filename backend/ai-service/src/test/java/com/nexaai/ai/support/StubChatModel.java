package com.nexaai.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A stub Spring AI {@link ChatModel}.
 *
 * <p><strong>An implementation, not a mock.</strong> Spring AI's {@code ChatModel} is the
 * interface this service is built around, so implementing it directly means routing, fallback,
 * retry and failure classification are all exercised against the real abstraction — no mocking
 * framework, no stubbed return values, and no ability to accidentally verify against a mock's own
 * assumptions.
 *
 * <p>Records every call, which is what makes the retry and fallback assertions possible: "was this
 * called three times" is the only honest way to say the retry policy worked.
 *
 * <p>Carries provider-reported usage metadata, so the token-count extraction in
 * {@code SpringAiProviderClient} is verified against a real {@link ChatResponse} rather than
 * assumed.
 */
public class StubChatModel implements ChatModel {

    private final String name;

    private String content = "stub answer";

    /** When set, every call throws this instead. */
    private RuntimeException failure;

    /** When set, the first {@code failFirstCalls} calls throw and the rest succeed. */
    private int failFirstCalls;

    /** Prompt tokens this stub reports. Null means "reported none", not "reported zero". */
    private Integer promptTokens = 11;

    /** Completion tokens this stub reports. */
    private Integer completionTokens = 7;

    private final AtomicInteger calls = new AtomicInteger();

    private final List<Prompt> prompts = new ArrayList<>();

    public StubChatModel(String name) {
        this.name = name;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        int callNumber = calls.incrementAndGet();

        if (failure != null) {
            throw failure;
        }
        if (callNumber <= failFirstCalls) {
            throw new IllegalStateException(
                    "stub transient failure " + callNumber + " for " + name);
        }

        Generation generation = new Generation(new AssistantMessage(content));
        return new ChatResponse(List.of(generation), metadata());
    }

    /** Provider-reported usage. Omitted entirely when both counts are null. */
    private ChatResponseMetadata metadata() {
        ChatResponseMetadata.Builder builder = ChatResponseMetadata.builder().model(name);
        if (promptTokens != null || completionTokens != null) {
            builder.usage(new DefaultUsage(promptTokens, completionTokens, null));
        }
        return builder.build();
    }

    // ------------------------------------------------------------------
    // Test controls
    // ------------------------------------------------------------------

    public StubChatModel returning(String content) {
        this.content = content;
        return this;
    }

    public StubChatModel failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public StubChatModel failingFirst(int attempts) {
        this.failFirstCalls = attempts;
        return this;
    }

    /** Makes the stub report no usage at all, which is distinct from reporting zero. */
    public StubChatModel reportingNoUsage() {
        this.promptTokens = null;
        this.completionTokens = null;
        return this;
    }

    public int callCount() {
        return calls.get();
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public Prompt lastPrompt() {
        if (prompts.isEmpty()) {
            throw new IllegalStateException("No prompt reached this model.");
        }
        return prompts.get(prompts.size() - 1);
    }

    public String name() {
        return name;
    }
}