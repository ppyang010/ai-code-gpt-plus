package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.enums.ChatModeEnum;
import com.example.aichat.common.enums.ChatRoleEnum;
import com.example.aichat.common.enums.MessageStatusEnum;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.ChatModelClient;
import com.example.aichat.infrastructure.ai.ChatModelClientRegistry;
import com.example.aichat.infrastructure.ai.ChatModelClientException;
import com.example.aichat.infrastructure.ai.ChatModelMessage;
import com.example.aichat.infrastructure.ai.ChatModelRequest;
import com.example.aichat.infrastructure.ai.ChatModelResponse;
import com.example.aichat.infrastructure.ai.ChatModelStreamChunk;
import com.example.aichat.infrastructure.ai.deepseek.DeepSeekProperties;
import com.example.aichat.modules.billing.entity.UserTokenUsageDO;
import com.example.aichat.modules.billing.mapper.UserTokenUsageMapper;
import com.example.aichat.modules.chat.dto.ChatMessageRegenerateRequest;
import com.example.aichat.modules.chat.dto.ChatMessageSendRequest;
import com.example.aichat.modules.chat.dto.stream.ChatStreamDeltaEvent;
import com.example.aichat.modules.chat.dto.stream.ChatStreamEndEvent;
import com.example.aichat.modules.chat.dto.stream.ChatStreamErrorEvent;
import com.example.aichat.modules.chat.dto.stream.ChatStreamStartEvent;
import com.example.aichat.modules.chat.entity.ChatMessageDO;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import com.example.aichat.modules.chat.mapper.ChatMessageMapper;
import com.example.aichat.modules.chat.mapper.ChatSessionMapper;
import com.example.aichat.modules.chat.service.ChatMessageService;
import com.example.aichat.modules.chat.service.ChatPromptResolver;
import com.example.aichat.modules.chat.vo.ChatMessageItemVO;
import com.example.aichat.modules.chat.vo.ChatMessageListVO;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.mapper.ApiCallLogMapper;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final int SESSION_STATUS_ACTIVE = 1;
    private static final Logger log = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private final ChatPromptResolver chatPromptResolver;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ChatModelClientRegistry chatModelClientRegistry;
    private final ApiCallLogMapper apiCallLogMapper;
    private final UserTokenUsageMapper userTokenUsageMapper;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties deepSeekProperties;

    public ChatMessageServiceImpl(
            ChatPromptResolver chatPromptResolver,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ModelConfigMapper modelConfigMapper,
            ChatModelClientRegistry chatModelClientRegistry,
            ApiCallLogMapper apiCallLogMapper,
            UserTokenUsageMapper userTokenUsageMapper,
            ObjectMapper objectMapper,
            DeepSeekProperties deepSeekProperties
    ) {
        this.chatPromptResolver = chatPromptResolver;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.chatModelClientRegistry = chatModelClientRegistry;
        this.apiCallLogMapper = apiCallLogMapper;
        this.userTokenUsageMapper = userTokenUsageMapper;
        this.objectMapper = objectMapper;
        this.deepSeekProperties = deepSeekProperties;
    }

    @Override
    public ChatMessageListVO listMessages(Long userId, Long sessionId) {
        ChatSessionDO session = getActiveSession(userId, sessionId);
        List<ChatMessageDO> messages = chatMessageMapper.selectBySessionIdOrderBySeqNo(sessionId);
        Map<Long, ModelConfigDO> modelMap = loadModelMap(messages.stream()
                .map(ChatMessageDO::getModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        ChatMessageListVO response = new ChatMessageListVO();
        response.setSessionId(session.getId());
        response.setTitle(session.getTitle());
        response.setModeCode(session.getModeCode());
        response.setDefaultModelId(session.getDefaultModelId());
        response.setMessageList(messages.stream().map(message -> toMessageItem(message, modelMap)).collect(Collectors.toList()));
        return response;
    }

    /**
     * 发送一条用户消息，并通过 SSE 异步推送 assistant 的流式回答。
     *
     * <p>该方法只负责同步阶段的请求校验、会话/模型解析、用户消息落库和异步任务派发；
     * 真正的模型调用、assistant 消息落库、增量事件推送、token 统计和供应商调用日志，
     * 都在 {@link #streamAssistantAnswer(Long, ChatSessionDO, ModelConfigDO, ChatModelRequest, int, SseEmitter)}
     * 中完成。这样可以让控制器尽快拿到 {@link SseEmitter}，避免 HTTP 请求线程被模型流式响应长期占用。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(Long userId, ChatMessageSendRequest request, SseEmitter emitter) {
        // 会话是消息归属、上下文读取和历史展示的核心维度；没有会话 id 时直接按会话不存在处理。
        if (request.getSessionId() == null) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        // 空消息不会进入模型调用，也不写入 chat_message，避免产生无意义的用户记录和上下文污染。
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }

        // 只允许向当前用户自己的有效会话发送消息；这里也会过滤已删除/非活跃会话。
        ChatSessionDO session = getActiveSession(userId, request.getSessionId());
        // 优先使用本次请求指定的模型；未指定时回落到会话默认模型，再由 resolveModel 做全局默认兜底。
        ModelConfigDO model = resolveModel(session, request.getModelId());

        // 本次请求的系统提示词由模式、会话和请求三层合并，保证模式切换能影响同一会话内的回答策略。
        String finalPrompt = chatPromptResolver.resolveSystemPrompt(
                request.getModeCode() != null ? request.getModeCode() : session.getModeCode(),
                session.getSystemPrompt(),
                request.getSystemPrompt()
        );

        // 在用户新消息入库前构造模型请求，使本次模型上下文只包含已存在的历史消息；
        // 当前用户输入通过 userContent 单独传给模型客户端，避免在 messages 中重复出现。
        ChatModelRequest modelRequest = buildModelRequest(session, model, request.getContent(), finalPrompt, request.getModeCode());

        // seq_no 采用同一会话内递增序号：用户消息占当前序号，assistant 回答占下一个序号。
        int nextSeqNo = nextSeqNo(session.getId());
        ChatMessageDO userMessage = buildUserMessage(userId, session.getId(), request.getContent(), nextSeqNo);
        // 用户消息先落库，保证即使后续模型调用失败，历史里也能保留用户实际发出的内容。
        chatMessageMapper.insert(userMessage);
        // 更新会话最后活跃时间，列表页可以立刻按最新发送行为排序。
        chatSessionMapper.updateLastMessageAt(session.getId(), LocalDateTime.now());

        // assistant 的占位消息、模型流式调用、SSE 增量推送和最终 token/日志回写都在异步流程里完成。
        streamAssistantAnswer(
                userId,
                session,
                model,
                modelRequest,
                nextSeqNo + 1,
                emitter
        );
    }

    @Override
    public void regenerateMessage(Long userId, ChatMessageRegenerateRequest request, SseEmitter emitter) {
        throw new BizException(ErrorCode.CHAT_REGENERATE_NOT_IMPLEMENTED);
    }

    private void streamAssistantAnswer(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            int assistantSeqNo,
            SseEmitter emitter
    ) {
        // 模型调用放到异步线程中执行，HTTP 请求线程可以尽快返回 SseEmitter 并持续推送增量事件。
        CompletableFuture.runAsync(() -> {
            ChatMessageDO assistantMessage = buildAssistantPlaceholder(userId, session.getId(), model, assistantSeqNo);
            try {
                chatMessageMapper.insert(assistantMessage);
                sendStart(session.getId(), assistantMessage.getId(), model, emitter);

                ChatModelClient chatModelClient = chatModelClientRegistry.resolve(modelRequest);
                boolean persistProviderUsage = shouldPersistProviderUsage(model, chatModelClient);
                long startedAt = System.currentTimeMillis();
                ChatModelResponse modelResponse = chatModelClient.streamChat(
                        modelRequest,
                        chunk -> emitChunk(emitter, assistantMessage.getId(), chunk)
                );

                // 流式结束后再一次性回写 assistant 正文和 token，避免每个增量 chunk 都打数据库。
                assistantMessage.setContent(modelResponse.getContent());
                assistantMessage.setFinishReason(modelResponse.getFinishReason());
                assistantMessage.setStatus(MessageStatusEnum.NORMAL.getCode());
                assistantMessage.setPromptTokens(defaultInt(modelResponse.getPromptTokens()));
                assistantMessage.setCompletionTokens(defaultInt(modelResponse.getCompletionTokens()));
                assistantMessage.setTotalTokens(defaultInt(modelResponse.getTotalTokens()));
                chatMessageMapper.updateById(assistantMessage);
                chatSessionMapper.updateLastMessageAt(session.getId(), LocalDateTime.now());
                persistSuccessUsage(
                        persistProviderUsage,
                        userId,
                        session,
                        model,
                        modelRequest,
                        assistantMessage,
                        modelResponse,
                        startedAt
                );

                ChatStreamEndEvent endEvent = new ChatStreamEndEvent();
                endEvent.setMessageId(assistantMessage.getId());
                endEvent.setFinishReason(modelResponse.getFinishReason());
                endEvent.setPromptTokens(defaultInt(modelResponse.getPromptTokens()));
                endEvent.setCompletionTokens(defaultInt(modelResponse.getCompletionTokens()));
                endEvent.setTotalTokens(defaultInt(modelResponse.getTotalTokens()));
                endEvent.setCreatedAt(LocalDateTime.now());
                emitter.send(SseEmitter.event().name("message_end").data(endEvent));
                emitter.complete();
            } catch (Exception exception) {
                try {
                    // 失败也要落一条 assistant 失败记录，前端刷新历史时可以看到上次请求失败。
                    assistantMessage.setStatus(MessageStatusEnum.FAILED.getCode());
                    assistantMessage.setFinishReason("error");
                    if (assistantMessage.getId() != null) {
                        chatMessageMapper.updateById(assistantMessage);
                    }
                    persistFailureUsage(userId, session, model, modelRequest, assistantMessage, exception);
                    ChatStreamErrorEvent errorEvent = new ChatStreamErrorEvent();
                    errorEvent.setMessageId(assistantMessage.getId());
                    errorEvent.setErrorCode("CHAT_STREAM_ERROR");
                    errorEvent.setErrorMessage(exception.getMessage());
                    emitter.send(SseEmitter.event().name("message_error").data(errorEvent));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(exception);
            }
        });
    }

    private void sendStart(Long sessionId, Long messageId, ModelConfigDO model, SseEmitter emitter) throws Exception {
        ChatStreamStartEvent startEvent = new ChatStreamStartEvent();
        startEvent.setSessionId(sessionId);
        startEvent.setMessageId(messageId);
        startEvent.setRole(ChatRoleEnum.ASSISTANT.getCode());
        startEvent.setModelId(model == null ? null : model.getId());
        startEvent.setModelCode(model == null ? null : model.getModelCode());
        startEvent.setModelName(model == null ? null : model.getModelName());
        emitter.send(SseEmitter.event().name("message_start").data(startEvent));
    }

    private ChatSessionDO getActiveSession(Long userId, Long sessionId) {
        ChatSessionDO session = chatSessionMapper.selectActiveById(sessionId, userId);
        if (session == null || !Objects.equals(session.getStatus(), SESSION_STATUS_ACTIVE)) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        return session;
    }

    private ModelConfigDO resolveModel(ChatSessionDO session, Long requestModelId) {
        Long modelId = requestModelId != null ? requestModelId : session.getDefaultModelId();
        if (modelId != null) {
            ModelConfigDO model = modelConfigMapper.selectEnabledById(modelId);
            if (model == null) {
                throw new BizException(ErrorCode.CHAT_MODEL_NOT_FOUND);
            }
            return model;
        }

        // 老会话可能没有 default_model_id，因此继续用配置里的默认模型兜底。
        ModelConfigDO defaultModel = modelConfigMapper.selectByModelCode(deepSeekProperties.getDefaultModel());
        if (defaultModel == null || !Objects.equals(defaultModel.getStatus(), 1)) {
            throw new BizException(ErrorCode.CHAT_DEFAULT_MODEL_NOT_CONFIGURED);
        }
        return defaultModel;
    }

    private int nextSeqNo(Long sessionId) {
        Integer current = chatMessageMapper.selectMaxSeqNo(sessionId);
        return current == null ? 1 : current + 1;
    }

    private ChatMessageDO buildUserMessage(Long userId, Long sessionId, String content, int seqNo) {
        ChatMessageDO entity = new ChatMessageDO();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRole(ChatRoleEnum.USER.getCode());
        entity.setSeqNo(seqNo);
        entity.setContent(content.trim());
        entity.setContentFormat("markdown");
        entity.setStatus(MessageStatusEnum.NORMAL.getCode());
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(0);
        return entity;
    }

    private ChatMessageDO buildAssistantPlaceholder(Long userId, Long sessionId, ModelConfigDO model, int seqNo) {
        ChatMessageDO entity = new ChatMessageDO();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRole(ChatRoleEnum.ASSISTANT.getCode());
        entity.setSeqNo(seqNo);
        entity.setContent("");
        entity.setContentFormat("markdown");
        entity.setModelId(model == null ? null : model.getId());
        entity.setStatus(MessageStatusEnum.GENERATING.getCode());
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(0);
        return entity;
    }

    private ChatModelRequest buildModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String finalPrompt,
            String requestModeCode
    ) {
        ChatModelRequest request = new ChatModelRequest();
        request.setSessionId(session.getId());
        request.setModelId(model == null ? null : model.getId());
        request.setModelCode(model == null ? null : model.getModelCode());
        request.setModelName(model == null ? null : model.getModelName());
        request.setModeCode(requestModeCode != null ? requestModeCode : session.getModeCode());
        request.setSystemPrompt(finalPrompt);
        request.setUserContent(userContent);
        request.setMessages(loadContextMessages(session.getId()));
        return request;
    }

    private List<ChatModelMessage> loadContextMessages(Long sessionId) {
        // 第一版只取最近 10 条上下文，控制 token 成本；后续可接入模型 contextWindow 做动态裁剪。
        return chatMessageMapper.selectLatestMessages(sessionId, 10).stream()
                .sorted((left, right) -> Integer.compare(left.getSeqNo(), right.getSeqNo()))
                .map(message -> new ChatModelMessage(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    private void emitChunk(SseEmitter emitter, Long messageId, ChatModelStreamChunk chunk) {
        if (chunk == null || chunk.isDone()) {
            return;
        }
        ChatStreamDeltaEvent deltaEvent = new ChatStreamDeltaEvent();
        deltaEvent.setMessageId(messageId);
        deltaEvent.setDelta(chunk.getDelta());
        try {
            emitter.send(SseEmitter.event().name("message_delta").data(deltaEvent));
        } catch (Exception exception) {
            throw new BizException(ErrorCode.CHAT_STREAM_ERROR);
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean shouldPersistProviderUsage(ModelConfigDO model, ChatModelClient chatModelClient) {
        // mock 不产生真实供应商消耗，避免把调试响应记入调用日志和 token 统计。
        return model != null
                && model.getId() != null
                && model.getProviderId() != null
                && chatModelClient != null
                && !"mock".equals(chatModelClient.clientCode());
    }

    private void persistSuccessUsage(
            boolean persistProviderUsage,
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            ChatMessageDO assistantMessage,
            ChatModelResponse modelResponse,
            long startedAt
    ) {
        if (!persistProviderUsage) {
            return;
        }
        try {
            // 统计日志是旁路能力，落库失败只写日志，不影响用户已经收到的模型响应。
            ApiCallLogDO apiCallLog = buildSuccessApiCallLog(
                    userId,
                    session,
                    model,
                    modelRequest,
                    assistantMessage,
                    modelResponse,
                    startedAt
            );
            apiCallLogMapper.insert(apiCallLog);
            userTokenUsageMapper.insert(buildUserTokenUsage(userId, session, model, assistantMessage, modelResponse, apiCallLog.getId()));
        } catch (Exception exception) {
            log.warn("Failed to persist model usage log for messageId={}", assistantMessage.getId(), exception);
        }
    }

    private void persistFailureUsage(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            ChatMessageDO assistantMessage,
            Exception exception
    ) {
        if (model == null || model.getId() == null || model.getProviderId() == null) {
            return;
        }
        try {
            apiCallLogMapper.insert(buildFailureApiCallLog(userId, session, model, modelRequest, assistantMessage, exception));
        } catch (Exception persistException) {
            log.warn("Failed to persist failure model log for messageId={}", assistantMessage.getId(), persistException);
        }
    }

    private ApiCallLogDO buildSuccessApiCallLog(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            ChatMessageDO assistantMessage,
            ChatModelResponse modelResponse,
            long startedAt
    ) {
        ApiCallLogDO entity = new ApiCallLogDO();
        entity.setUserId(userId);
        entity.setSessionId(session.getId());
        entity.setMessageId(assistantMessage.getId());
        entity.setProviderId(model.getProviderId());
        entity.setModelId(model.getId());
        entity.setRequestId(modelResponse.getRequestId());
        entity.setIsStream(1);
        entity.setSuccessFlag(1);
        entity.setHttpStatus(defaultInt(modelResponse.getHttpStatus()));
        entity.setLatencyMs(toLatencyMs(startedAt));
        entity.setPromptTokens(defaultInt(modelResponse.getPromptTokens()));
        entity.setCompletionTokens(defaultInt(modelResponse.getCompletionTokens()));
        entity.setTotalTokens(defaultInt(modelResponse.getTotalTokens()));
        entity.setEstimatedCost(BigDecimal.ZERO);
        entity.setRequestPayload(toJsonSafely(buildRequestPayload(modelRequest)));
        entity.setResponsePayload(toJsonSafely(buildSuccessResponsePayload(modelResponse)));
        return entity;
    }

    private ApiCallLogDO buildFailureApiCallLog(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            ChatMessageDO assistantMessage,
            Exception exception
    ) {
        ApiCallLogDO entity = new ApiCallLogDO();
        entity.setUserId(userId);
        entity.setSessionId(session.getId());
        entity.setMessageId(assistantMessage.getId());
        entity.setProviderId(model.getProviderId());
        entity.setModelId(model.getId());
        entity.setIsStream(1);
        entity.setSuccessFlag(0);
        entity.setHttpStatus(resolveHttpStatus(exception));
        entity.setLatencyMs(null);
        entity.setPromptTokens(0);
        entity.setCompletionTokens(0);
        entity.setTotalTokens(0);
        entity.setEstimatedCost(BigDecimal.ZERO);
        entity.setErrorCode(resolveErrorCode(exception));
        entity.setErrorMessage(truncate(resolveErrorMessage(exception), 1000));
        entity.setRequestPayload(toJsonSafely(buildRequestPayload(modelRequest)));
        entity.setResponsePayload(resolveFailureResponsePayload(exception));
        return entity;
    }

    private UserTokenUsageDO buildUserTokenUsage(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatMessageDO assistantMessage,
            ChatModelResponse modelResponse,
            Long apiCallLogId
    ) {
        UserTokenUsageDO entity = new UserTokenUsageDO();
        entity.setUserId(userId);
        entity.setSessionId(session.getId());
        entity.setMessageId(assistantMessage.getId());
        entity.setApiCallLogId(apiCallLogId);
        entity.setProviderId(model.getProviderId());
        entity.setModelId(model.getId());
        entity.setPromptTokens(defaultInt(modelResponse.getPromptTokens()));
        entity.setCompletionTokens(defaultInt(modelResponse.getCompletionTokens()));
        entity.setTotalTokens(defaultInt(modelResponse.getTotalTokens()));
        entity.setEstimatedCost(BigDecimal.ZERO);
        entity.setStatDate(LocalDate.now());
        return entity;
    }

    private Integer toLatencyMs(long startedAt) {
        long latency = System.currentTimeMillis() - startedAt;
        return latency > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latency;
    }

    private Map<String, Object> buildRequestPayload(ChatModelRequest modelRequest) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelCode", modelRequest.getModelCode());
        payload.put("modeCode", modelRequest.getModeCode());
        payload.put("contextMessageCount", modelRequest.getMessages() == null ? 0 : modelRequest.getMessages().size());
        payload.put("systemPromptPreview", truncate(modelRequest.getSystemPrompt(), 500));
        payload.put("userContentPreview", truncate(modelRequest.getUserContent(), 500));
        return payload;
    }

    private Map<String, Object> buildSuccessResponsePayload(ChatModelResponse modelResponse) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", modelResponse.getRequestId());
        payload.put("httpStatus", modelResponse.getHttpStatus());
        payload.put("finishReason", modelResponse.getFinishReason());
        payload.put("contentPreview", truncate(modelResponse.getContent(), 500));
        payload.put("promptTokens", defaultInt(modelResponse.getPromptTokens()));
        payload.put("completionTokens", defaultInt(modelResponse.getCompletionTokens()));
        payload.put("totalTokens", defaultInt(modelResponse.getTotalTokens()));
        return payload;
    }

    private Integer resolveHttpStatus(Exception exception) {
        if (exception instanceof ChatModelClientException modelClientException) {
            return modelClientException.getHttpStatus();
        }
        return null;
    }

    private String resolveErrorCode(Exception exception) {
        if (exception instanceof ChatModelClientException modelClientException) {
            return modelClientException.getErrorCode();
        }
        if (exception instanceof BizException bizException) {
            return bizException.getMessage();
        }
        return "CHAT_STREAM_ERROR";
    }

    private String resolveErrorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String resolveFailureResponsePayload(Exception exception) {
        if (exception instanceof ChatModelClientException modelClientException) {
            return truncate(modelClientException.getResponsePayload(), 1000);
        }
        return null;
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.warn("Failed to serialize model payload", exception);
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Map<Long, ModelConfigDO> loadModelMap(Set<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return modelIds.stream()
                .map(modelConfigMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModelConfigDO::getId, java.util.function.Function.identity(), (left, right) -> left));
    }

    private ChatMessageItemVO toMessageItem(ChatMessageDO message, Map<Long, ModelConfigDO> modelMap) {
        ChatMessageItemVO item = new ChatMessageItemVO();
        item.setMessageId(message.getId());
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setContentFormat(message.getContentFormat());
        item.setSeqNo(message.getSeqNo());
        item.setModelId(message.getModelId());
        item.setFinishReason(message.getFinishReason());
        item.setStatus(message.getStatus());
        item.setPromptTokens(message.getPromptTokens());
        item.setCompletionTokens(message.getCompletionTokens());
        item.setTotalTokens(message.getTotalTokens());
        item.setCreatedAt(message.getCreatedAt());

        if (message.getModelId() != null) {
            ModelConfigDO model = modelMap.get(message.getModelId());
            if (model != null) {
                item.setModelCode(model.getModelCode());
                item.setModelName(model.getModelName());
            }
        }
        return item;
    }
}
