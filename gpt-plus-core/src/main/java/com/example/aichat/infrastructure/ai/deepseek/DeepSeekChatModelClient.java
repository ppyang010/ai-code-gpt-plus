package com.example.aichat.infrastructure.ai.deepseek;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.ChatModelClient;
import com.example.aichat.infrastructure.ai.ChatModelClientException;
import com.example.aichat.infrastructure.ai.ChatModelMessage;
import com.example.aichat.infrastructure.ai.ChatModelRequest;
import com.example.aichat.infrastructure.ai.ChatModelResponse;
import com.example.aichat.infrastructure.ai.ChatModelStreamChunk;
import com.example.aichat.infrastructure.ai.ChatStreamInterruptedException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class DeepSeekChatModelClient implements ChatModelClient {

    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekChatModelClient(DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
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
    public ChatModelResponse streamChat(ChatModelRequest request, Consumer<ChatModelStreamChunk> chunkConsumer) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BizException(ErrorCode.DEEPSEEK_API_KEY_NOT_CONFIGURED);
        }

        // DeepSeek 的 token usage 只会在流式尾包返回，因此请求必须打开 include_usage。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", resolveModel(request));
        payload.put("stream", Boolean.TRUE);
        payload.put("stream_options", Map.of("include_usage", Boolean.TRUE));
        payload.put("messages", buildMessages(request));

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(buildChatCompletionsUrl()))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = readBody(response.body());
                throw buildHttpException(response.statusCode(), errorBody);
            }

            return readStreamResponse(request, response, chunkConsumer);
        } catch (ChatStreamInterruptedException exception) {
            throw exception;
        } catch (ChatModelClientException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ChatModelClientException("DEEPSEEK_STREAM_IO_ERROR", 502, "DEEPSEEK_STREAM_IO_ERROR", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatModelClientException("DEEPSEEK_STREAM_INTERRUPTED", 500, "DEEPSEEK_STREAM_INTERRUPTED", exception.getMessage());
        } catch (Exception exception) {
            throw new ChatModelClientException("DEEPSEEK_STREAM_PARSE_ERROR", 500, "DEEPSEEK_STREAM_PARSE_ERROR", exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private ChatModelResponse readStreamResponse(
            ChatModelRequest request,
            HttpResponse<InputStream> response,
            Consumer<ChatModelStreamChunk> chunkConsumer
    ) throws IOException {
        StringBuilder contentBuilder = new StringBuilder();
        String requestId = null;
        String finishReason = "stop";
        Integer promptTokens = 0;
        Integer completionTokens = 0;
        Integer totalTokens = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // DeepSeek 按 SSE data 行返回增量内容；非 data 行只作为协议噪声跳过。
                if (!StringUtils.hasText(line) || !line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    chunkConsumer.accept(new ChatModelStreamChunk("", true));
                    break;
                }

                Map<String, Object> chunk = objectMapper.readValue(data, new TypeReference<>() {});
                if (!StringUtils.hasText(requestId)) {
                    requestId = asText(chunk.get("id"));
                }

                Map<String, Object> usage = castMap(chunk.get("usage"));
                if (usage != null) {
                    // usage 通常出现在最后一个 chunk，覆盖前面的 0 值即可。
                    promptTokens = readInt(usage, "prompt_tokens");
                    completionTokens = readInt(usage, "completion_tokens");
                    totalTokens = readInt(usage, "total_tokens");
                }

                List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }

                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> delta = castMap(firstChoice.get("delta"));
                if (delta != null) {
                    String deltaContent = asText(delta.get("content"));
                    if (StringUtils.hasText(deltaContent)) {
                        contentBuilder.append(deltaContent);
                        chunkConsumer.accept(new ChatModelStreamChunk(deltaContent, false));
                    }
                }

                String currentFinishReason = asText(firstChoice.get("finish_reason"));
                if (StringUtils.hasText(currentFinishReason)) {
                    finishReason = currentFinishReason;
                }
            }
        }

        ChatModelResponse modelResponse = new ChatModelResponse();
        modelResponse.setRequestId(requestId);
        modelResponse.setHttpStatus(response.statusCode());
        modelResponse.setModelCode(resolveModel(request));
        modelResponse.setModelName(request.getModelName());
        modelResponse.setContent(contentBuilder.toString());
        modelResponse.setFinishReason(finishReason);
        modelResponse.setPromptTokens(promptTokens);
        modelResponse.setCompletionTokens(completionTokens);
        modelResponse.setTotalTokens(totalTokens);
        return modelResponse;
    }

    @Override
    public String clientCode() {
        return "deepseek";
    }

    private String resolveModel(ChatModelRequest request) {
        return StringUtils.hasText(request.getModelCode()) ? request.getModelCode() : properties.getDefaultModel();
    }

    private String buildChatCompletionsUrl() {
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.deepseek.com/chat/completions";
        }
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/chat/completions";
    }

    private List<Map<String, String>> buildMessages(ChatModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(newMessage("system", request.getSystemPrompt()));
        }
        // 历史上下文在 service 层已经按 seqNo 排序，这里只负责过滤空消息并拼装供应商格式。
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String asText(Object value) {
        return value == null ? null : Objects.toString(value, null);
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

    private String readBody(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        }
    }

    private ChatModelClientException buildHttpException(int httpStatus, String errorBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(errorBody, new TypeReference<>() {});
            Map<String, Object> error = castMap(body.get("error"));
            String message = error == null ? "DEEPSEEK_HTTP_ERROR" : asText(error.get("message"));
            String errorCode = error == null ? "DEEPSEEK_HTTP_ERROR" : asText(error.get("code"));
            return new ChatModelClientException(
                    StringUtils.hasText(message) ? message : "DEEPSEEK_HTTP_ERROR",
                    httpStatus,
                    StringUtils.hasText(errorCode) ? errorCode : "DEEPSEEK_HTTP_ERROR",
                    errorBody
            );
        } catch (Exception ignored) {
            return new ChatModelClientException("DEEPSEEK_HTTP_ERROR", httpStatus, "DEEPSEEK_HTTP_ERROR", errorBody);
        }
    }
}
