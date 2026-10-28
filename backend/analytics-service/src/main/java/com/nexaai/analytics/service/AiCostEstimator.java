package com.nexaai.analytics.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AiCostEstimator {

    /**
     * Estimates AI provider token cost in USD.
     * Note: This is an estimated LLM provider execution cost, NOT Razorpay billing data.
     */
    public BigDecimal estimateCostUsd(String model, long promptTokens, long completionTokens) {
        String normalized = model != null ? model.toLowerCase() : "";

        double promptRatePerMillion;
        double completionRatePerMillion;

        if (normalized.contains("gpt-4o-mini")) {
            promptRatePerMillion = 0.15;
            completionRatePerMillion = 0.60;
        } else if (normalized.contains("gpt-4o")) {
            promptRatePerMillion = 2.50;
            completionRatePerMillion = 10.00;
        } else if (normalized.contains("claude-3-5-sonnet") || normalized.contains("sonnet")) {
            promptRatePerMillion = 3.00;
            completionRatePerMillion = 15.00;
        } else if (normalized.contains("embedding")) {
            promptRatePerMillion = 0.02;
            completionRatePerMillion = 0.0;
        } else {
            promptRatePerMillion = 0.20;
            completionRatePerMillion = 0.80;
        }

        double promptCost = (promptTokens * promptRatePerMillion) / 1_000_000.0;
        double completionCost = (completionTokens * completionRatePerMillion) / 1_000_000.0;
        double total = promptCost + completionCost;

        return BigDecimal.valueOf(total).setScale(6, RoundingMode.HALF_UP);
    }
}
