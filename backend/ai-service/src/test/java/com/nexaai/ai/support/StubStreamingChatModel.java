package com.nexaai.ai.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * A stub Spring AI {@link StreamingChatModel}.
 *
 * <p>Emits one {@link ChatResponse} per chunk, as separate signals. Deliberately not one response
 * containing the whole answer: a stream that delivers everything in a single signal would pass a
 * test that only concatenated the output, while the browser would show nothing until the end —
 * which is the exact failure streaming exists to prevent.
 */
public class StubStreamingChatModel implements StreamingChatModel {

    private final String name;

    private List<String> chunks = List.of("Hello", " from", " ", "the model");

    private final AtomicInteger calls = new AtomicInteger();

    /** When set, the stream errors with this instead of emitting. */
    private RuntimeException failure;

    /** When set, the first N calls error and the rest succeed. */
    private int failFirstCalls;

    public StubStreamingChatModel(String name) {
        this.name = name;
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        int callNumber = calls.incrementAndGet();
        return Flux.defer(() -> {
            if (failure != null) {
                return Flux.error(failure);
            }
            if (callNumber <= failFirstCalls) {
                return Flux.error(new IllegalStateException("stub stream failure " + callNumber));
            }
            return Flux.fromIterable(chunks)
                    .map(chunk -> new ChatResponse(List.of(
                            new Generation(new AssistantMessage(chunk)))));
        });
    }

    public StubStreamingChatModel emitting(List<String> chunks) {
        this.chunks = List.copyOf(chunks);
        return this;
    }

    public StubStreamingChatModel failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    public StubStreamingChatModel failingFirst(int attempts) {
        this.failFirstCalls = attempts;
        return this;
    }

    public int callCount() {
        return calls.get();
    }
}