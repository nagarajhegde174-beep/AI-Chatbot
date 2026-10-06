package com.nexaai.ai.support;

import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

/**
 * Spring AI beans for the HTTP-level tests.
 *
 * <p><strong>The real production beans are exercised, not replaced.</strong> Configuration points
 * {@code chat-model-bean} and {@code streaming-model-bean} at these, so the actual
 * {@code SpringAiProviderClient}, {@code ModelCatalog}, {@code ModelRouter} and
 * {@code AiController} all run — including bean lookup by name, which is the part most likely to
 * be wired wrong and the part a hand-built object graph would skip.
 *
 * <p>The beans are thin delegators rather than the stubs themselves, so a test can swap the
 * stub's behaviour without the context restarting: the bean is resolved once at startup, so
 * returning the stub directly would freeze the first test's instance for every test after it.
 */
@TestConfiguration
public class ProviderStubs {

    private static volatile StubChatModel chat = new StubChatModel("test-model");
    private static volatile StubStreamingChatModel streaming =
            new StubStreamingChatModel("test-model");

    @Bean("testChatModel")
    public ChatModel testChatModel() {
        return new DelegatingChatModel();
    }

    @Bean("testStreamingChatModel")
    public StreamingChatModel testStreamingChatModel() {
        return new DelegatingStreamingChatModel();
    }

    public static StubChatModel chat() {
        return chat;
    }

    public static StubStreamingChatModel streaming() {
        return streaming;
    }

    /** Gives the next test a clean stub. Called from each test's setup. */
    public static void reset() {
        chat = new StubChatModel("test-model");
        streaming = new StubStreamingChatModel("test-model");
    }

    /** Forwards to whichever stub is current. */
    private static final class DelegatingChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return chat.call(prompt);
        }
    }

    /** Forwards to whichever stub is current. */
    private static final class DelegatingStreamingChatModel implements StreamingChatModel {

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return streaming.stream(prompt);
        }
    }

    /**
     * A ready-made {@link ChatResponse} carrying usage.
     *
     * <p>Used by tests that need to assert on the translation of a response they did not get
     * from the stub, without going through the stub to build it.
     */
    public static ChatResponse response(String text, int input, int output) {
        Generation generation = new Generation(new AssistantMessage(text));
        return new ChatResponse(List.of(generation), ChatResponseMetadata.builder()
                .model("test-model")
                .usage(new DefaultUsage(input, output, null))
                .build());
    }
}