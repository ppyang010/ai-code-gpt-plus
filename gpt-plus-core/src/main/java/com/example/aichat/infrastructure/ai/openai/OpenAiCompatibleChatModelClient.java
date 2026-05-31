package com.example.aichat.infrastructure.ai.openai;

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
 * 通用 OpenAI-compatible 流式模型客户端，复用 /chat/completions SSE 协议接入多供应商。
 */
@Component
public class OpenAiCompatibleChatModelClient implements ChatModelClient {

    /** 当前客户端在调用日志中的统一编码。 */
    private static final String CLIENT_CODE = "openai-compatible";
    /** OpenAI-compatible 流式接口的相对路径。 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** 多供应商本地密钥与基础地址配置。 */
    private final OpenAiCompatibleProperties properties;
    /** JSON 序列化器，负责供应商请求体和 SSE chunk 解析。 */
    private final ObjectMapper objectMapper;
    /** JDK HTTP 客户端，统一承载兼容供应商的出站请求。 */
    private final HttpClient httpClient;

    /**
     * 初始化通用供应商客户端，复用同一个 HTTP 客户端处理流式请求。
     */
    public OpenAiCompatibleChatModelClient(OpenAiCompatibleProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 仅当请求模型所属 providerCode 在本地兼容供应商配置中启用且配置了 API Key 时才接管请求。
     */
    @Override
    public boolean supports(ChatModelRequest request) {
        // DeepSeek 保留专用客户端，避免同一 provider 同时被通用兼容客户端抢占。
        if (request != null && "deepseek".equalsIgnoreCase(request.getProviderCode())) {
            return false;
        }
        OpenAiCompatibleProperties.Provider provider = this.resolveConfiguredProvider(request);
        return provider != null
                && provider.isEnabled()
                && StringUtils.hasText(provider.getApiKey())
                && StringUtils.hasText(request.getModelCode());
    }

    /**
     * 将统一模型请求转换为 OpenAI-compatible 流式请求，并把 SSE delta 持续回调给上层。
     */
    @Override
    @SuppressWarnings("unchecked")
    public ChatModelResponse streamChat(ChatModelRequest request, Consumer<ChatModelStreamChunk> chunkConsumer) {
        OpenAiCompatibleProperties.Provider provider = this.requireConfiguredProvider(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", request.getModelCode());
        payload.put("stream", Boolean.TRUE);
        payload.put("stream_options", Map.of("include_usage", Boolean.TRUE));
        payload.put("messages", this.buildMessages(request));

        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = this.buildHttpRequest(request, provider, requestBody);
            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
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
            throw new ChatModelClientException("OPENAI_COMPATIBLE_STREAM_IO_ERROR", 502, "OPENAI_COMPATIBLE_STREAM_IO_ERROR", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatModelClientException("OPENAI_COMPATIBLE_STREAM_INTERRUPTED", 500, "OPENAI_COMPATIBLE_STREAM_INTERRUPTED", exception.getMessage());
        } catch (Exception exception) {
            throw new ChatModelClientException("OPENAI_COMPATIBLE_STREAM_PARSE_ERROR", 500, "OPENAI_COMPATIBLE_STREAM_PARSE_ERROR", exception.getMessage());
        }
    }

    @Override
    public String clientCode() {
        return CLIENT_CODE;
    }

    /**
     * 构造带鉴权和可选默认头的 HTTP 请求。
     */
    private HttpRequest buildHttpRequest(
            ChatModelRequest request,
            OpenAiCompatibleProperties.Provider provider,
            String requestBody
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(this.buildChatCompletionsUrl(request, provider)))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + provider.getApiKey());

        if (provider.getDefaultHeaders() != null) {
            // 额外 header 只允许配置非空 key/value，避免覆盖 JDK 对空 header 的校验异常。
            provider.getDefaultHeaders().forEach((name, value) -> {
                if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                    builder.header(name, value);
                }
            });
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)).build();
    }

    /**
     * 解析 OpenAI-compatible SSE 响应，抽取增量文本、finishReason 和 token usage。
     */
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
                    requestId = this.asText(chunk.get("id"));
                }

                Map<String, Object> usage = this.castMap(chunk.get("usage"));
                if (usage != null) {
                    promptTokens = this.readInt(usage, "prompt_tokens");
                    completionTokens = this.readInt(usage, "completion_tokens");
                    totalTokens = this.readInt(usage, "total_tokens");
                }

                List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                if (choices == null || choices.isEmpty()) {
                    continue;
                }

                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> delta = this.castMap(firstChoice.get("delta"));
                if (delta != null) {
                    String deltaContent = this.asText(delta.get("content"));
                    if (StringUtils.hasText(deltaContent)) {
                        contentBuilder.append(deltaContent);
                        chunkConsumer.accept(new ChatModelStreamChunk(deltaContent, false));
                    }
                }

                String currentFinishReason = this.asText(firstChoice.get("finish_reason"));
                if (StringUtils.hasText(currentFinishReason)) {
                    finishReason = currentFinishReason;
                }
            }
        }

        ChatModelResponse modelResponse = new ChatModelResponse();
        modelResponse.setRequestId(requestId);
        modelResponse.setHttpStatus(response.statusCode());
        modelResponse.setModelCode(request.getModelCode());
        modelResponse.setModelName(request.getModelName());
        modelResponse.setContent(contentBuilder.toString());
        modelResponse.setFinishReason(finishReason);
        modelResponse.setPromptTokens(promptTokens);
        modelResponse.setCompletionTokens(completionTokens);
        modelResponse.setTotalTokens(totalTokens);
        return modelResponse;
    }

    /**
     * 拼装供应商请求消息，过滤空历史，避免把无效上下文传给模型。
     */
    private List<Map<String, String>> buildMessages(ChatModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(this.newMessage("system", request.getSystemPrompt()));
        }
        for (ChatModelMessage modelMessage : request.getMessages()) {
            if (modelMessage != null && StringUtils.hasText(modelMessage.getRole()) && StringUtils.hasText(modelMessage.getContent())) {
                messages.add(this.newMessage(modelMessage.getRole(), modelMessage.getContent()));
            }
        }
        if (StringUtils.hasText(request.getUserContent())) {
            messages.add(this.newMessage("user", request.getUserContent()));
        }
        return messages;
    }

    /**
     * 构造供应商 message 对象，保持 role/content 字段顺序稳定。
     */
    private Map<String, String> newMessage(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 根据请求中的 providerCode 读取本地供应商配置。
     */
    private OpenAiCompatibleProperties.Provider resolveConfiguredProvider(ChatModelRequest request) {
        if (!properties.isEnabled() || request == null || !StringUtils.hasText(request.getProviderCode())) {
            return null;
        }
        return properties.getProvider(request.getProviderCode());
    }

    /**
     * 强校验兼容供应商配置，避免 streamChat 被直接调用时静默走空配置。
     */
    private OpenAiCompatibleProperties.Provider requireConfiguredProvider(ChatModelRequest request) {
        OpenAiCompatibleProperties.Provider provider = this.resolveConfiguredProvider(request);
        if (provider == null || !provider.isEnabled()) {
            throw new ChatModelClientException(
                    "OpenAI-compatible provider is not configured",
                    503,
                    "OPENAI_COMPATIBLE_PROVIDER_NOT_CONFIGURED",
                    null
            );
        }
        if (!StringUtils.hasText(provider.getApiKey())) {
            throw new ChatModelClientException(
                    "OpenAI-compatible API Key is not configured",
                    503,
                    "OPENAI_COMPATIBLE_API_KEY_NOT_CONFIGURED",
                    null
            );
        }
        return provider;
    }

    /**
     * 优先使用本地配置 base-url，其次使用数据库供应商 base_url，并自动补齐 /chat/completions。
     */
    private String buildChatCompletionsUrl(ChatModelRequest request, OpenAiCompatibleProperties.Provider provider) {
        String baseUrl = StringUtils.hasText(provider.getBaseUrl()) ? provider.getBaseUrl() : request.getProviderBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new ChatModelClientException(
                    "OpenAI-compatible base URL is not configured",
                    503,
                    "OPENAI_COMPATIBLE_BASE_URL_NOT_CONFIGURED",
                    null
            );
        }

        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith(CHAT_COMPLETIONS_PATH)) {
            return normalized;
        }
        return normalized + CHAT_COMPLETIONS_PATH;
    }

    /**
     * 将 Jackson 解析出的 Object 安全转换为 Map，兼容 usage/error 等嵌套结构。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * 将供应商响应字段统一转成字符串，空值保持为空。
     */
    private String asText(Object value) {
        return value == null ? null : Objects.toString(value, null);
    }

    /**
     * 从 usage 结构中读取 token 数，兼容数字和字符串两种返回类型。
     */
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

    /**
     * 读取错误响应体，用于供应商失败日志和前端错误提示定位。
     */
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

    /**
     * 兼容 OpenAI 标准 error 结构，同时保留非标准供应商原始响应。
     */
    private ChatModelClientException buildHttpException(int httpStatus, String errorBody) {
        try {
            Map<String, Object> body = objectMapper.readValue(errorBody, new TypeReference<>() {});
            Map<String, Object> error = this.castMap(body.get("error"));
            String message = error == null ? "OPENAI_COMPATIBLE_HTTP_ERROR" : this.asText(error.get("message"));
            String errorCode = error == null ? "OPENAI_COMPATIBLE_HTTP_ERROR" : this.asText(error.get("code"));
            return new ChatModelClientException(
                    StringUtils.hasText(message) ? message : "OPENAI_COMPATIBLE_HTTP_ERROR",
                    httpStatus,
                    StringUtils.hasText(errorCode) ? errorCode : "OPENAI_COMPATIBLE_HTTP_ERROR",
                    errorBody
            );
        } catch (Exception ignored) {
            return new ChatModelClientException("OPENAI_COMPATIBLE_HTTP_ERROR", httpStatus, "OPENAI_COMPATIBLE_HTTP_ERROR", errorBody);
        }
    }
}
