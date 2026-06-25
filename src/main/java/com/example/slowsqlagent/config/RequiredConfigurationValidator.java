package com.example.slowsqlagent.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class RequiredConfigurationValidator implements ApplicationRunner {

    private final Environment environment;
    private final AgentProperties properties;

    public RequiredConfigurationValidator(Environment environment, AgentProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();
        requireConfigured(errors, "spring.ai.openai.api-key", environment.getProperty("spring.ai.openai.api-key"));
        requireConfigured(errors, "spring.ai.openai.base-url", environment.getProperty("spring.ai.openai.base-url"));
        requireConfigured(errors, "spring.ai.openai.chat.options.model",
                environment.getProperty("spring.ai.openai.chat.options.model"));
        requireConfigured(errors, "agent.database.url", properties.getDatabase().getUrl());
        requireConfigured(errors, "agent.database.username", properties.getDatabase().getUsername());
        requireConfigured(errors, "agent.database.default-schema", properties.getDatabase().getDefaultSchema());
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Required configuration is missing or still uses placeholders: "
                    + String.join(", ", errors)
                    + ". Please update config/application.yml.");
        }
    }

    private void requireConfigured(List<String> errors, String key, String value) {
        if (!StringUtils.hasText(value) || isPlaceholder(value)) {
            errors.add(key);
        }
    }

    private boolean isPlaceholder(String value) {
        String lower = value.toLowerCase();
        return lower.contains("请填写")
                || lower.contains("your_")
                || lower.contains("readonly_")
                || lower.contains("password");
    }
}
