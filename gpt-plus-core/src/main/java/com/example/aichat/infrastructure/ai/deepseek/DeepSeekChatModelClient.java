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

/**
 * DeepSeek 专用流式模型客户端，负责 DeepSeek 官方接口的 SSE 请求和响应解析。
 */
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

    /**
     * 优先按 providerCode 判断是否归属 DeepSeek；旧数据没有 providerCode 时才回退到模型前缀。
     */
    @Override
    public boolean supports(ChatModelRequest request) {
        if (!this.properties.isEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(this.properties.getApiKey())) {
            return false;
        }
        if (request == null) {
            return false;
        }
        if (StringUtils.hasText(request.getProviderCode())) {
            return "deepseek".equalsIgnoreCase(request.getProviderCode());
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
        payload.put("model", this.resolveModel(request));
        payload.put("stream", Boolean.TRUE);
        payload.put("stream_options", Map.of("include_usage", Boolean.TRUE));
        payload.put("messages", this.buildMessages(request));
        this.appendThinkingConfig(payload, request);

        try {
            String requestBody = this.objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(this.buildChatCompletionsUrl()))
                    .timeout(Duration.ofMinutes(3))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + this.properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = this.httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody = this.readBody(response.body());
                throw this.buildHttpException(response.statusCode(), errorBody);
            }

            return this.readStreamResponse(request, response, chunkConsumer);
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
        StringBuilder reasoningBuilder = new StringBuilder();
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
                    String reasoningDelta = this.asText(delta.get("reasoning_content"));
                    if (StringUtils.hasText(reasoningDelta)) {
                        reasoningBuilder.append(reasoningDelta);
                        chunkConsumer.accept(new ChatModelStreamChunk("", reasoningDelta, false));
                    }
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
        modelResponse.setModelCode(this.resolveModel(request));
        modelResponse.setModelName(request.getModelName());
        modelResponse.setContent(contentBuilder.toString());
        modelResponse.setReasoningContent(reasoningBuilder.toString());
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

    /**
     * DeepSeek 原生思考模式由 `thinking.type` 控制，这里把统一请求字段翻译成供应商协议。
     */
    private void appendThinkingConfig(Map<String, Object> payload, ChatModelRequest request) {
        if (request == null || request.getEnableDeepThinking() == null) {
            return;
        }
        payload.put("thinking", Map.of("type", request.getEnableDeepThinking() ? "enabled" : "disabled"));
    }

    /**
     * 组装 DeepSeek 请求消息；当前版本只发送文本内容，图片附件仍仅作为聊天附件保存和回显。
     */
    private List<Map<String, Object>> buildMessages(ChatModelRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(this.newTextMessage("system", request.getSystemPrompt()));
        }
        // 历史上下文在 service 层已经按 seqNo 排序，这里只负责过滤空消息并拼装供应商格式。
        for (ChatModelMessage modelMessage : request.getMessages()) {
            if (modelMessage != null && StringUtils.hasText(modelMessage.getRole()) && StringUtils.hasText(modelMessage.getContent())) {
                messages.add(this.newTextMessage(modelMessage.getRole(), modelMessage.getContent()));
            }
        }
        if (StringUtils.hasText(request.getUserContent())) {
            messages.add(this.newTextMessage("user", request.getUserContent()));
        }
        return messages;
    }

    /**
     * 构造纯文本消息对象，保持 role/content 字段顺序稳定。
     */
    private Map<String, Object> newTextMessage(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
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
        if (value instanceof Number) {
            return ((Number) value).intValue();
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
