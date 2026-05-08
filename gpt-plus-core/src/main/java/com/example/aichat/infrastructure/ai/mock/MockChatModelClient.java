package com.example.aichat.infrastructure.ai.mock;

import com.example.aichat.common.enums.ChatModeEnum;
import com.example.aichat.infrastructure.ai.ChatModelClient;
import com.example.aichat.infrastructure.ai.ChatModelRequest;
import com.example.aichat.infrastructure.ai.ChatModelResponse;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class MockChatModelClient implements ChatModelClient {

    @Override
    public boolean supports(ChatModelRequest request) {
        return true;
    }

    @Override
    public ChatModelResponse streamChat(ChatModelRequest request, Consumer<String> deltaConsumer) {
        String currentMode = ChatModeEnum.fromCodeOrDefault(request.getModeCode()).getCode();
        String content = String.format(
                "This is a mock streaming response for frontend integration.%n"
                        + "Current mode: %s%n"
                        + "User input: %s%n"
                        + "Resolved system prompt:%n"
                        + "%s%n"
                        + "Replace this mock flow with model adapter calls, database writes, usage logging, and session updates.",
                currentMode,
                safeText(request.getUserContent()),
                safeText(request.getSystemPrompt())
        );

        for (String chunk : content.split("\n")) {
            deltaConsumer.accept(chunk + "\n");
        }

        ChatModelResponse response = new ChatModelResponse();
        response.setModelCode(request.getModelCode() != null ? request.getModelCode() : "mock-model");
        response.setModelName(request.getModelName() != null ? request.getModelName() : "Mock Model");
        response.setContent(content);
        response.setFinishReason("stop");
        response.setPromptTokens(120);
        response.setCompletionTokens(180);
        response.setTotalTokens(300);
        return response;
    }

    @Override
    public String clientCode() {
        return "mock";
    }

    private String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "(empty)" : value.trim();
    }
}
