package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ErrorCode;
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
import com.example.aichat.infrastructure.ai.ChatStreamInterruptedException;
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
    private static final int CONTEXT_MESSAGE_LIMIT = 10;
    private static final int PARTIAL_CONTENT_FLUSH_CHARS = 160;
    private static final long PARTIAL_CONTENT_FLUSH_INTERVAL_MS = 1500L;
    private static final int RESUME_PREFIX_MAX_LENGTH = 2000;
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
     * 都在 {@link #streamAssistantAnswer(Long, ChatSessionDO, ModelConfigDO, ChatModelRequest, ChatMessageDO, boolean, String, SseEmitter)}
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

        ChatMessageDO assistantMessage = buildAssistantPlaceholder(userId, session.getId(), model, nextSeqNo + 1);
        // assistant 的占位消息、模型流式调用、SSE 增量推送和最终 token/日志回写都在异步流程里完成。
        streamAssistantAnswer(
                userId,
                session,
                model,
                modelRequest,
                assistantMessage,
                true,
                "",
                emitter
        );
    }

    @Override
    public void regenerateMessage(Long userId, ChatMessageRegenerateRequest request, SseEmitter emitter) {
        if (request.getSessionId() == null) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (request.getRegenerateMessageId() == null) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }

        ChatSessionDO session = getActiveSession(userId, request.getSessionId());
        ChatMessageDO assistantMessage = getSessionMessage(session.getId(), request.getRegenerateMessageId());
        if (!ChatRoleEnum.ASSISTANT.getCode().equals(assistantMessage.getRole())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_INTERRUPTIBLE);
        }
        if (Objects.equals(assistantMessage.getStatus(), MessageStatusEnum.GENERATING.getCode())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_ALREADY_GENERATING);
        }
        if (!Objects.equals(assistantMessage.getStatus(), MessageStatusEnum.INTERRUPTED.getCode())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_INTERRUPTIBLE);
        }

        ChatMessageDO previousUserMessage = getPreviousUserMessage(session.getId(), assistantMessage.getSeqNo());
        ModelConfigDO model = resolveModel(session, request.getModelId() != null ? request.getModelId() : assistantMessage.getModelId());
        String basePrompt = chatPromptResolver.resolveSystemPrompt(
                request.getModeCode() != null ? request.getModeCode() : session.getModeCode(),
                session.getSystemPrompt(),
                null
        );
        ChatModelRequest modelRequest = buildResumeModelRequest(
                session,
                model,
                previousUserMessage.getContent(),
                basePrompt,
                request.getModeCode(),
                assistantMessage
        );

        // 继续生成前先原子占住该消息，避免同一条中断消息被并发点击多次。
        int updatedRows = chatMessageMapper.updateStatusByIdAndCurrentStatus(
                assistantMessage.getId(),
                MessageStatusEnum.INTERRUPTED.getCode(),
                MessageStatusEnum.GENERATING.getCode()
        );
        if (updatedRows == 0) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_ALREADY_GENERATING);
        }

        resetAssistantMessageForResume(assistantMessage, model);
        streamAssistantAnswer(
                userId,
                session,
                model,
                modelRequest,
                assistantMessage,
                false,
                assistantMessage.getContent(),
                emitter
        );
    }

    private void streamAssistantAnswer(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            ChatMessageDO assistantMessage,
            boolean shouldInsertAssistantMessage,
            String persistedPrefix,
            SseEmitter emitter
    ) {
        // 模型调用放到异步线程中执行，HTTP 请求线程可以尽快返回 SseEmitter 并持续推送增量事件。
        CompletableFuture.runAsync(() -> {
            StringBuilder contentBuilder = new StringBuilder(StringUtils.hasText(persistedPrefix) ? persistedPrefix : "");
            long[] lastPersistedAt = {System.currentTimeMillis()};
            int[] lastPersistedLength = {contentBuilder.length()};
            try {
                if (shouldInsertAssistantMessage) {
                    chatMessageMapper.insert(assistantMessage);
                } else {
                    chatMessageMapper.updateById(assistantMessage);
                }
                sendStart(session.getId(), assistantMessage.getId(), model, emitter);

                ChatModelClient chatModelClient = chatModelClientRegistry.resolve(modelRequest);
                boolean persistProviderUsage = shouldPersistProviderUsage(model, chatModelClient);
                long startedAt = System.currentTimeMillis();
                ChatModelResponse modelResponse = chatModelClient.streamChat(
                        modelRequest,
                        chunk -> appendAndEmitChunk(
                                emitter,
                                assistantMessage,
                                chunk,
                                contentBuilder,
                                lastPersistedAt,
                                lastPersistedLength
                        )
                );

                // 流式结束后统一回写完整正文和 token；期间的部分内容已按阈值做过增量持久化。
                assistantMessage.setContent(contentBuilder.toString());
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
                    if (isClientStreamInterrupted(exception)) {
                        persistInterruptedMessage(assistantMessage, contentBuilder.toString());
                    } else if (hasRecoverableContent(contentBuilder)) {
                        persistInterruptedMessage(assistantMessage, contentBuilder.toString());
                    } else {
                        persistFailedMessage(assistantMessage);
                    }

                    if (!isClientStreamInterrupted(exception) && shouldPersistFailureUsage(exception)) {
                        persistFailureUsage(userId, session, model, modelRequest, assistantMessage, exception);
                    }

                    if (!isClientStreamInterrupted(exception) && !hasRecoverableContent(contentBuilder)) {
                        ChatStreamErrorEvent errorEvent = new ChatStreamErrorEvent();
                        errorEvent.setMessageId(assistantMessage.getId());
                        errorEvent.setErrorCode("CHAT_STREAM_ERROR");
                        errorEvent.setErrorMessage(exception.getMessage());
                        emitter.send(SseEmitter.event().name("message_error").data(errorEvent));
                    }
                } catch (Exception finalizeException) {
                    log.warn("Failed to finalize interrupted chat stream for messageId={}", assistantMessage.getId(), finalizeException);
                }
                emitter.complete();
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

    private ChatMessageDO getSessionMessage(Long sessionId, Long messageId) {
        ChatMessageDO message = chatMessageMapper.selectById(messageId);
        if (message == null || !Objects.equals(message.getSessionId(), sessionId)) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
        return message;
    }

    private ChatMessageDO getPreviousUserMessage(Long sessionId, Integer assistantSeqNo) {
        ChatMessageDO previousUserMessage = chatMessageMapper.selectPreviousUserMessage(sessionId, assistantSeqNo);
        if (previousUserMessage == null) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_FOUND, "未找到可继续生成的用户消息");
        }
        return previousUserMessage;
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

    private void resetAssistantMessageForResume(ChatMessageDO assistantMessage, ModelConfigDO model) {
        assistantMessage.setModelId(model == null ? null : model.getId());
        assistantMessage.setStatus(MessageStatusEnum.GENERATING.getCode());
        assistantMessage.setFinishReason(null);
        assistantMessage.setPromptTokens(0);
        assistantMessage.setCompletionTokens(0);
        assistantMessage.setTotalTokens(0);
    }

    private ChatModelRequest buildModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String finalPrompt,
            String requestModeCode
    ) {
        return buildModelRequest(
                session,
                model,
                userContent,
                finalPrompt,
                requestModeCode,
                loadContextMessages(session.getId(), CONTEXT_MESSAGE_LIMIT)
        );
    }

    private ChatModelRequest buildResumeModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String basePrompt,
            String requestModeCode,
            ChatMessageDO interruptedAssistantMessage
    ) {
        String resumePrompt = buildResumePrompt(basePrompt, interruptedAssistantMessage.getContent());
        return buildModelRequest(
                session,
                model,
                userContent,
                resumePrompt,
                requestModeCode,
                loadContextMessagesBeforeSeqNo(session.getId(), interruptedAssistantMessage.getSeqNo(), CONTEXT_MESSAGE_LIMIT)
        );
    }

    private ChatModelRequest buildModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String finalPrompt,
            String requestModeCode,
            List<ChatModelMessage> contextMessages
    ) {
        ChatModelRequest request = new ChatModelRequest();
        request.setSessionId(session.getId());
        request.setModelId(model == null ? null : model.getId());
        request.setModelCode(model == null ? null : model.getModelCode());
        request.setModelName(model == null ? null : model.getModelName());
        request.setModeCode(requestModeCode != null ? requestModeCode : session.getModeCode());
        request.setSystemPrompt(finalPrompt);
        request.setUserContent(userContent);
        request.setMessages(contextMessages);
        return request;
    }

    private List<ChatModelMessage> loadContextMessages(Long sessionId, int limit) {
        // 第一版只取最近若干条上下文，控制 token 成本；后续可接入模型 contextWindow 做动态裁剪。
        return chatMessageMapper.selectLatestMessages(sessionId, limit).stream()
                .sorted((left, right) -> Integer.compare(left.getSeqNo(), right.getSeqNo()))
                .map(message -> new ChatModelMessage(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    private List<ChatModelMessage> loadContextMessagesBeforeSeqNo(Long sessionId, Integer beforeSeqNo, int limit) {
        return chatMessageMapper.selectLatestMessagesBeforeSeqNo(sessionId, beforeSeqNo, limit).stream()
                .sorted((left, right) -> Integer.compare(left.getSeqNo(), right.getSeqNo()))
                .map(message -> new ChatModelMessage(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    private String buildResumePrompt(String basePrompt, String assistantContent) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(basePrompt)) {
            prompt.append(basePrompt.trim()).append("\n\n");
        }
        prompt.append("下面是一段被中断的回答，请从它停止的位置继续往后写。\n");
        prompt.append("不要重复已经输出的内容，不要重新开头，不要改写前文语气。\n");
        prompt.append("已生成前缀：\n");
        prompt.append(extractResumePrefix(assistantContent));
        return prompt.toString();
    }

    private String extractResumePrefix(String assistantContent) {
        if (!StringUtils.hasText(assistantContent)) {
            return "";
        }
        if (assistantContent.length() <= RESUME_PREFIX_MAX_LENGTH) {
            return assistantContent;
        }
        // 继续生成只需要最近一段前缀帮助模型衔接，避免把整篇半成品重新塞进提示词导致 token 膨胀。
        return assistantContent.substring(assistantContent.length() - RESUME_PREFIX_MAX_LENGTH);
    }

    private void appendAndEmitChunk(
            SseEmitter emitter,
            ChatMessageDO assistantMessage,
            ChatModelStreamChunk chunk,
            StringBuilder contentBuilder,
            long[] lastPersistedAt,
            int[] lastPersistedLength
    ) {
        if (chunk == null || chunk.isDone()) {
            return;
        }
        if (StringUtils.hasText(chunk.getDelta())) {
            contentBuilder.append(chunk.getDelta());
            persistPartialContentIfNeeded(assistantMessage, contentBuilder, lastPersistedAt, lastPersistedLength);
        }
        ChatStreamDeltaEvent deltaEvent = new ChatStreamDeltaEvent();
        deltaEvent.setMessageId(assistantMessage.getId());
        deltaEvent.setDelta(chunk.getDelta());
        try {
            emitter.send(SseEmitter.event().name("message_delta").data(deltaEvent));
        } catch (Exception exception) {
            throw new ChatStreamInterruptedException("CLIENT_STREAM_INTERRUPTED", exception);
        }
    }

    private void persistPartialContentIfNeeded(
            ChatMessageDO assistantMessage,
            StringBuilder contentBuilder,
            long[] lastPersistedAt,
            int[] lastPersistedLength
    ) {
        int deltaLength = contentBuilder.length() - lastPersistedLength[0];
        long now = System.currentTimeMillis();
        if (deltaLength < PARTIAL_CONTENT_FLUSH_CHARS && now - lastPersistedAt[0] < PARTIAL_CONTENT_FLUSH_INTERVAL_MS) {
            return;
        }

        assistantMessage.setContent(contentBuilder.toString());
        chatMessageMapper.updateById(assistantMessage);
        lastPersistedAt[0] = now;
        lastPersistedLength[0] = contentBuilder.length();
    }

    private boolean hasRecoverableContent(StringBuilder contentBuilder) {
        return contentBuilder != null && StringUtils.hasText(contentBuilder.toString());
    }

    private void persistInterruptedMessage(ChatMessageDO assistantMessage, String content) {
        if (assistantMessage.getId() == null) {
            return;
        }
        assistantMessage.setContent(content);
        assistantMessage.setStatus(MessageStatusEnum.INTERRUPTED.getCode());
        assistantMessage.setFinishReason("interrupted");
        chatMessageMapper.updateById(assistantMessage);
    }

    private void persistFailedMessage(ChatMessageDO assistantMessage) {
        if (assistantMessage.getId() == null) {
            return;
        }
        assistantMessage.setStatus(MessageStatusEnum.FAILED.getCode());
        assistantMessage.setFinishReason("error");
        chatMessageMapper.updateById(assistantMessage);
    }

    private boolean shouldPersistFailureUsage(Exception exception) {
        return exception instanceof ChatModelClientException;
    }

    private boolean isClientStreamInterrupted(Exception exception) {
        return exception instanceof ChatStreamInterruptedException;
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
            String responsePayload = truncate(modelClientException.getResponsePayload(), 1000);
            if (!StringUtils.hasText(responsePayload)) {
                return null;
            }
            try {
                return objectMapper.writeValueAsString(objectMapper.readTree(responsePayload));
            } catch (Exception ignored) {
                return toJsonSafely(Map.of("rawResponse", responsePayload));
            }
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
