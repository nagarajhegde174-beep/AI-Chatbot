package com.nexaai.ai.agent.tools;

import com.nexaai.ai.agent.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherTool implements AgentTool {

    private static final ToolDefinition DEFINITION = ToolDefinition.of(
            "weather",
            "Retrieve current weather conditions, temperature, humidity, and forecasts for a given city or region.",
            "TOOL_WEATHER",
            false,
            Map.of("location", "City and country (e.g., 'San Francisco, US', 'London, UK')")
    );

    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }

    @Override
    public Object execute(Map<String, Object> arguments) throws Exception {
        Object locObj = arguments.get("location");
        if (locObj == null) {
            locObj = arguments.get("city");
        }
        if (locObj == null) {
            throw new IllegalArgumentException("Missing required parameter 'location'");
        }
        String location = locObj.toString().trim();

        // Controlled weather synthesis
        return Map.of(
                "location", location,
                "temperatureCelsius", 21.5,
                "condition", "Partly Cloudy",
                "humidityPercent", 62,
                "windSpeedKph", 14.2,
                "forecast", "Mild conditions with light breeze throughout the day."
        );
    }
}
