package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CalculatorTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "calculator",
            "Perform mathematical computations such as addition, subtraction, multiplication, division, exponentiation, and square roots.",
            "TOOL_CALCULATOR",
            false,
            Map.of("expression", "String arithmetic expression (e.g., '14 * 25', 'sqrt(144)', '1250 / 5')")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object exprObj = arguments.get("expression");
        if (exprObj == null) {
            exprObj = arguments.get("input");
        }
        if (exprObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'expression'");
        }
        String expr = exprObj.toString().trim();
        return evaluateExpression(expr);
    }

    private Map<String, Object> evaluateExpression(String expr) {
        String cleaned = expr.replaceAll("\\s+", "").toLowerCase();
        try {
            if (cleaned.startsWith("sqrt(") && cleaned.endsWith(")")) {
                double val = Double.parseDouble(cleaned.substring(5, cleaned.length() - 1));
                return Map.of("expression", expr, "result", Math.sqrt(val));
            }
            if (cleaned.contains("+")) {
                String[] parts = cleaned.split("\\+");
                double res = Double.parseDouble(parts[0]) + Double.parseDouble(parts[1]);
                return Map.of("expression", expr, "result", res);
            }
            if (cleaned.contains("-") && !cleaned.startsWith("-")) {
                int dashIdx = cleaned.indexOf('-', 1);
                double a = Double.parseDouble(cleaned.substring(0, dashIdx));
                double b = Double.parseDouble(cleaned.substring(dashIdx + 1));
                return Map.of("expression", expr, "result", a - b);
            }
            if (cleaned.contains("*")) {
                String[] parts = cleaned.split("\\*");
                double res = Double.parseDouble(parts[0]) * Double.parseDouble(parts[1]);
                return Map.of("expression", expr, "result", res);
            }
            if (cleaned.contains("/")) {
                String[] parts = cleaned.split("/");
                double denom = Double.parseDouble(parts[1]);
                if (denom == 0.0) {
                    throw new ArithmeticException("Division by zero");
                }
                double res = Double.parseDouble(parts[0]) / denom;
                return Map.of("expression", expr, "result", res);
            }
            if (cleaned.contains("^")) {
                String[] parts = cleaned.split("\\^");
                double res = Math.pow(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
                return Map.of("expression", expr, "result", res);
            }
            // fallback plain number parse
            double num = Double.parseDouble(cleaned);
            return Map.of("expression", expr, "result", num);
        } catch (Exception e) {
            return Map.of("expression", expr, "error", "Failed to evaluate expression: " + e.getMessage());
        }
    }
}
