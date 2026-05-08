package com.example.aichat.infrastructure.ai.deepseek;

import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.ChatModelClient;
import com.example.aichat.infrastructure.ai.ChatModelMessage;
import com.example.aichat.infrastructure.ai.ChatModelRequest;
import com.example.aichat.infrastructure.ai.ChatModelResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class DeepSeekChatModelClient implements ChatModelClient {

    private final DeepSeekProperties properties;
    private final RestClient restClient;

    public DeepSeekChatModelClient(DeepSeekProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean supports(ChatModelRequest request) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            return false;
        }
        return StringUtils.hasText(request.getModelCode()) && request.getModelCode().startsWith("deepseek");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChatModelResponse streamChat(ChatModelRequest request, Consumer<String> deltaConsumer) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BizException(503, "DEEPSEEK_API_KEY_NOT_CONFIGURED");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModel(request));
        payload.put("stream", Boolean.FALSE);
        payload.put("messages", buildMessages(request));

        Map<String, Object> responseBody = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (responseBody == null) {
            throw new BizException(502, "DEEPSEEK_EMPTY_RESPONSE");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BizException(502, "DEEPSEEK_EMPTY_CHOICES");
        }

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        String content = message == null ? "" : Objects.toString(message.get("content"), "");
        if (StringUtils.hasText(content)) {
            deltaConsumer.accept(content);
        }

        Map<String, Object> usage = (Map<String, Object>) responseBody.get("usage");

        ChatModelResponse response = new ChatModelResponse();
        response.setModelCode(Objects.toString(responseBody.get("model"), resolveModel(request)));
        response.setModelName(request.getModelName());
        response.setContent(content);
        response.setFinishReason(Objects.toString(firstChoice.get("finish_reason"), "stop"));
        response.setPromptTokens(readInt(usage, "prompt_tokens"));
        response.setCompletionTokens(readInt(usage, "completion_tokens"));
        response.setTotalTokens(readInt(usage, "total_tokens"));
        return response;
    }

    @Override
    public String clientCode() {
        return "deepseek";
    }

    private String resolveModel(ChatModelRequest request) {
        return StringUtils.hasText(request.getModelCode()) ? request.getModelCode() : properties.getDefaultModel();
    }

    private List<Map<String, String>> buildMessages(ChatModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(newMessage("system", request.getSystemPrompt()));
        }
        for (ChatModelMessage modelMessage : request.getMessages()) {
            if (modelMessage != null && StringUtils.hasText(modelMessage.getRole()) && StringUtils.hasText(modelMessage.getContent())) {
                messages.add(newMessage(modelMessage.getRole(), modelMessage.getContent()));
            }
        }
        if (StringUtils.hasText(request.getUserContent())) {
            messages.add(newMessage("user", request.getUserContent()));
        }
        return messages;
    }

    private Map<String, String> newMessage(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Integer readInt(Map<String, Object> usage, String key) {
        if (usage == null || usage.get(key) == null) {
            return 0;
        }
        Object value = usage.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
