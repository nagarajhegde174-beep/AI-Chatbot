package com.nexaai.ai.support;

import com.nexaai.ai.config.AiProperties;
import org.springframework.context.support.GenericApplicationContext;

/**
 * A minimal application context holding the Spring AI beans a provider client looks up by name.
 *
 * <p>Bean-name lookup is how {@code SpringAiProviderClient} finds a provider's ChatModel, so
 * testing it against a hand-built object graph would skip the part most likely to break. This
 * registers beans the way the real context would, under the names configuration points at.
 *
 * <p>Static because the client resolves beans through an {@code ApplicationContext}, and a real
 * per-test context would mean rebuilding it for every assertion about bean absence — which is the
 * interesting case here. {@link #reset()} gives each test a clean factory instead.
 */
public final class TestContext {

    private static GenericApplicationContext context = newContext();

    private TestContext() {
    }

    private static GenericApplicationContext newContext() {
        GenericApplicationContext fresh = new GenericApplicationContext();
        fresh.refresh();
        return fresh;
    }

    public static GenericApplicationContext context() {
        return context;
    }

    /** Registers a singleton under {@code name}, as a Spring AI bean would be. */
    public static void put(String name, Object bean) {
        context.getBeanFactory().registerSingleton(name, bean);
    }

    public static boolean contains(String name) {
        return context.containsBean(name);
    }

    /** Empties the factory so one test's beans cannot satisfy another's assertions. */
    public static void reset() {
        context.close();
        context = newContext();
    }

    /**
     * A catalog with one model per provider plus a second OpenAI model.
     *
     * <p>Two models on one provider so same-provider fallback is exercisable, and one each on
     * Gemini and Groq so cross-provider behaviour can be asserted. Built fresh per call: sharing
     * one instance across test classes would let one test's configuration leak into another's
     * assertions.
     */
    public static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getDefaults().setModel("nexa-default");

        properties.getProviders().put("OPENAI", provider("OPENAI"));
        properties.getProviders().put("GEMINI", provider("GEMINI"));
        properties.getProviders().put("GROQ", provider("GROQ"));

        properties.getModels().add(model("nexa-default", "OPENAI", 1, true, true));
        properties.getModels().add(model("nexa-openai-mini", "OPENAI", 2, true, true));
        properties.getModels().add(model("nexa-gemini-flash", "GEMINI", 1, true, true));
        properties.getModels().add(model("nexa-groq-llama", "GROQ", 1, true, true));
        properties.getModels().add(model("nexa-fixed", "GROQ", 5, false, false));

        return properties;
    }

    private static AiProperties.Provider provider(String id) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setEnabled(true);
        provider.setApiKey("test-key-" + id);
        provider.setBaseUrl("https://example.invalid/v1");
        return provider;
    }

    private static AiProperties.Model model(String name, String provider, int priority,
                                            boolean temperature, boolean allowFallback) {
        AiProperties.Model model = new AiProperties.Model();
        model.setName(name);
        model.setProvider(provider);
        model.setPriority(priority);
        model.setSupportsTemperature(temperature);
        model.setAllowFallback(allowFallback);
        model.setMaxInputTokens(8000);
        model.setMaxOutputTokens(1000);
        return model;
    }
}