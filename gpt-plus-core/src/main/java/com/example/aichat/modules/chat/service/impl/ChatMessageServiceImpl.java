package com.example.aichat.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aichat.common.enums.ChatModeEnum;
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
import com.example.aichat.modules.chat.dto.stream.ChatStreamReasoningDeltaEvent;
import com.example.aichat.modules.chat.dto.stream.ChatStreamStartEvent;
import com.example.aichat.modules.chat.entity.ChatMessageDO;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import com.example.aichat.modules.chat.mapper.ChatMessageMapper;
import com.example.aichat.modules.chat.mapper.ChatSessionMapper;
import com.example.aichat.modules.chat.service.ChatMessageService;
import com.example.aichat.modules.chat.service.ChatPromptResolver;
import com.example.aichat.modules.chat.service.WebSearchContext;
import com.example.aichat.modules.chat.service.WebSearchIntentResolution;
import com.example.aichat.modules.chat.service.WebSearchIntentResolver;
import com.example.aichat.modules.chat.service.WebSearchResultItem;
import com.example.aichat.modules.chat.service.WebSearchService;
import com.example.aichat.modules.chat.vo.ChatMessageItemVO;
import com.example.aichat.modules.chat.vo.ChatMessageListVO;
import com.example.aichat.modules.file.service.FileAssetService;
import com.example.aichat.modules.file.vo.FileAssetItemVO;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import com.example.aichat.modules.model.mapper.ApiCallLogMapper;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import com.example.aichat.modules.model.mapper.ModelProviderMapper;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 聊天消息核心服务，负责消息落库、模型请求组装、SSE 推送和模型调用日志写入。
 */
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final int SESSION_STATUS_ACTIVE = 1;
    private static final int CONTEXT_MESSAGE_LIMIT = 10;
    private static final int PARTIAL_CONTENT_FLUSH_CHARS = 160;
    private static final long PARTIAL_CONTENT_FLUSH_INTERVAL_MS = 1500L;
    private static final int RESUME_PREFIX_MAX_LENGTH = 2000;
    /** 自动会话标题最长保留字符数，避免左侧列表标题过长。 */
    private static final int AUTO_SESSION_TITLE_MAX_LENGTH = 30;
    /** 只有这些默认标题会被首条用户消息自动替换，保护用户手动改过的标题。 */
    private static final Set<String> DEFAULT_SESSION_TITLES = Set.of("新会话", "New Chat", "新对话");
    private static final Logger log = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private final ChatPromptResolver chatPromptResolver;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    /** 模型供应商读取器，用于把模型配置解析成真实客户端可识别的 providerCode。 */
    private final ModelProviderMapper modelProviderMapper;
    private final FileAssetService fileAssetService;
    private final ChatModelClientRegistry chatModelClientRegistry;
    private final ApiCallLogMapper apiCallLogMapper;
    private final UserTokenUsageMapper userTokenUsageMapper;
    private final ObjectMapper objectMapper;
    private final DeepSeekProperties deepSeekProperties;
    private final WebSearchIntentResolver webSearchIntentResolver;
    /** 联网搜索领域服务，负责搜索执行、摘要构造和上下文返回。 */
    private final WebSearchService webSearchService;

    public ChatMessageServiceImpl(
            ChatPromptResolver chatPromptResolver,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ModelConfigMapper modelConfigMapper,
            ModelProviderMapper modelProviderMapper,
            FileAssetService fileAssetService,
            ChatModelClientRegistry chatModelClientRegistry,
            ApiCallLogMapper apiCallLogMapper,
            UserTokenUsageMapper userTokenUsageMapper,
            ObjectMapper objectMapper,
            DeepSeekProperties deepSeekProperties,
            WebSearchIntentResolver webSearchIntentResolver,
            WebSearchService webSearchService
    ) {
        this.chatPromptResolver = chatPromptResolver;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.modelProviderMapper = modelProviderMapper;
        this.fileAssetService = fileAssetService;
        this.chatModelClientRegistry = chatModelClientRegistry;
        this.apiCallLogMapper = apiCallLogMapper;
        this.userTokenUsageMapper = userTokenUsageMapper;
        this.objectMapper = objectMapper;
        this.deepSeekProperties = deepSeekProperties;
        this.webSearchIntentResolver = webSearchIntentResolver;
        this.webSearchService = webSearchService;
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
        WebSearchIntentResolution webSearchResolution = webSearchIntentResolver.resolve(
                request.getWebSearchMode(),
                request.getEnableWebSearch(),
                request.getContent()
        );
        request.setWebSearchMode(webSearchResolution.getMode());
        request.setEnableWebSearch(webSearchResolution.isEnabled());

        // 只允许向当前用户自己的有效会话发送消息；这里也会过滤已删除/非活跃会话。
        ChatSessionDO session = this.getActiveSession(userId, request.getSessionId());
        // 优先使用本次请求指定的模型；未指定时回落到会话默认模型，再由 resolveModel 做全局默认兜底。
        ModelConfigDO model = this.resolveModel(session, request.getModelId());
        List<FileAssetItemVO> attachments = this.fileAssetService.requireOwnedCompletedAssets(userId, request.getAttachmentIds());
        // 图片附件会进一步转换为多模态 image_url 内容块，尝试真正透传给支持视觉输入的供应商。
        List<String> userImageUrls = this.fileAssetService.buildOwnedImageDataUrls(userId, request.getAttachmentIds());
        // 思考模式现在只表示模型原生思考开关；显式字段优先，其次按请求 mode，再回落到会话 mode。
        Boolean enableDeepThinking = this.resolveEnableDeepThinking(
                request.getEnableDeepThinking(),
                request.getModeCode(),
                session.getModeCode()
        );
        request.setEnableDeepThinking(enableDeepThinking);

        // 当前 quick / expert 不再切默认 prompt，只保留会话级和请求级附加提示词合并能力。
        String finalPrompt = this.chatPromptResolver.resolveSystemPrompt(
                session.getSystemPrompt(),
                request.getSystemPrompt()
        );
        // 后端统一执行意图判定后的搜索，前端只负责传 mode，不参与搜索规则和供应商调用。
        WebSearchContext webSearchContext = this.webSearchService.search(request.getContent(), webSearchResolution);
        finalPrompt = this.appendWebSearchPrompt(finalPrompt, webSearchContext);

        // 在用户新消息入库前构造模型请求，使本次模型上下文只包含已存在的历史消息；
        // 当前用户输入通过 userContent 单独传给模型客户端，避免在 messages 中重复出现。
        ChatModelRequest modelRequest = this.buildModelRequest(
                session,
                model,
                request.getContent(),
                finalPrompt,
                request.getModeCode(),
                enableDeepThinking,
                userImageUrls
        );

        // seq_no 采用同一会话内递增序号：用户消息占当前序号，assistant 回答占下一个序号。
        int nextSeqNo = this.nextSeqNo(session.getId());
        ChatMessageDO userMessage = this.buildUserMessage(userId, session.getId(), request.getContent(), attachments, nextSeqNo);
        // 用户消息先落库，保证即使后续模型调用失败，历史里也能保留用户实际发出的内容。
        this.chatMessageMapper.insert(userMessage);
        // 首条消息发送后，用用户真实问题替换默认标题，降低左侧会话列表里多个“新会话”的识别成本。
        this.autoUpdateDefaultSessionTitle(session, userMessage.getContent(), nextSeqNo);
        // 更新会话最后活跃时间，列表页可以立刻按最新发送行为排序。
        this.chatSessionMapper.updateLastMessageAt(session.getId(), LocalDateTime.now());
        log.info(
                "Accepted chat message send userId={} sessionId={} userMessageId={} nextAssistantSeqNo={} modelId={} modelCode={} enableDeepThinking={} attachmentCount={} webSearchMode={} webSearchEnabled={} webSearchRuleId={}",
                userId,
                session.getId(),
                userMessage.getId(),
                nextSeqNo + 1,
                model == null ? null : model.getId(),
                model == null ? null : model.getModelCode(),
                enableDeepThinking,
                attachments.size(),
                webSearchResolution.getMode(),
                webSearchResolution.isEnabled(),
                webSearchResolution.getMatchedRuleId()
        );

        ChatMessageDO assistantMessage = this.buildAssistantPlaceholder(userId, session.getId(), model, nextSeqNo + 1);
        // assistant metadata 记录搜索上下文，刷新列表和中断后继续生成都从这里复用。
        assistantMessage.setMetadata(this.mergeAssistantThinkingMetadata(
                this.buildAssistantMessageMetadata(webSearchContext),
                enableDeepThinking
        ));
        // assistant 的占位消息、模型流式调用、SSE 增量推送和最终 token/日志回写都在异步流程里完成。
        this.streamAssistantAnswer(
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

        ChatSessionDO session = this.getActiveSession(userId, request.getSessionId());
        ChatMessageDO assistantMessage = this.getSessionMessage(session.getId(), request.getRegenerateMessageId());
        if (!ChatRoleEnum.ASSISTANT.getCode().equals(assistantMessage.getRole())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_INTERRUPTIBLE);
        }
        if (Objects.equals(assistantMessage.getStatus(), MessageStatusEnum.GENERATING.getCode())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_ALREADY_GENERATING);
        }
        if (!Objects.equals(assistantMessage.getStatus(), MessageStatusEnum.INTERRUPTED.getCode())) {
            throw new BizException(ErrorCode.CHAT_MESSAGE_NOT_INTERRUPTIBLE);
        }

        ChatMessageDO previousUserMessage = this.getPreviousUserMessage(session.getId(), assistantMessage.getSeqNo());
        ModelConfigDO model = this.resolveModel(session, request.getModelId() != null ? request.getModelId() : assistantMessage.getModelId());
        List<Long> previousAttachmentIds = this.parseMessageAttachments(previousUserMessage.getMetadata()).stream()
                .map(FileAssetItemVO::getFileId)
                .filter(Objects::nonNull)
                .toList();
        List<String> userImageUrls = this.fileAssetService.buildOwnedImageDataUrls(userId, previousAttachmentIds);
        Boolean enableDeepThinking = this.resolveEnableDeepThinking(
                request.getEnableDeepThinking(),
                request.getModeCode(),
                session.getModeCode()
        );
        request.setEnableDeepThinking(enableDeepThinking);
        String basePrompt = this.chatPromptResolver.resolveSystemPrompt(
                session.getSystemPrompt(),
                null
        );
        // 继续生成不重新搜索，避免同一条回答前后引用来源变化。
        basePrompt = this.appendWebSearchPrompt(basePrompt, this.parseWebSearchContext(assistantMessage.getMetadata()));
        ChatModelRequest modelRequest = this.buildResumeModelRequest(
                session,
                model,
                previousUserMessage.getContent(),
                basePrompt,
                request.getModeCode(),
                enableDeepThinking,
                userImageUrls,
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

        this.resetAssistantMessageForResume(assistantMessage, model);
        assistantMessage.setMetadata(this.mergeAssistantThinkingMetadata(assistantMessage.getMetadata(), enableDeepThinking));
        log.info(
                "Accepted chat regenerate userId={} sessionId={} messageId={} modelId={} modelCode={} enableDeepThinking={}",
                userId,
                session.getId(),
                assistantMessage.getId(),
                model == null ? null : model.getId(),
                model == null ? null : model.getModelCode(),
                enableDeepThinking
        );
        this.streamAssistantAnswer(
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
            StringBuilder reasoningBuilder = new StringBuilder(this.loadPersistedReasoningContent(assistantMessage.getMetadata()));
            long[] lastPersistedAt = {System.currentTimeMillis()};
            int[] lastPersistedLength = {contentBuilder.length()};
            String clientCode = "unknown";
            long startedAt = System.currentTimeMillis();
            try {
                if (shouldInsertAssistantMessage) {
                    chatMessageMapper.insert(assistantMessage);
                } else {
                    chatMessageMapper.updateById(assistantMessage);
                }
                sendStart(session.getId(), assistantMessage, model, emitter);

                ChatModelClient chatModelClient = chatModelClientRegistry.resolve(modelRequest);
                clientCode = chatModelClient.clientCode();
                boolean persistProviderUsage = shouldPersistProviderUsage(model, chatModelClient);
                startedAt = System.currentTimeMillis();
                log.info(
                        "Starting chat model stream userId={} sessionId={} messageId={} clientCode={} modelCode={} resume={}",
                        userId,
                        session.getId(),
                        assistantMessage.getId(),
                        clientCode,
                        modelRequest.getModelCode(),
                        !shouldInsertAssistantMessage
                );
                ChatModelResponse modelResponse = chatModelClient.streamChat(
                        modelRequest,
                        chunk -> appendAndEmitChunk(
                                emitter,
                                assistantMessage,
                                chunk,
                                contentBuilder,
                                reasoningBuilder,
                                lastPersistedAt,
                                lastPersistedLength
                        )
                );

                // 流式结束后统一回写完整正文和 token；期间的部分内容已按阈值做过增量持久化。
                assistantMessage.setContent(contentBuilder.toString());
                assistantMessage.setMetadata(this.mergeAssistantMessageMetadata(assistantMessage.getMetadata(), reasoningBuilder.toString()));
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
                long elapsedMs = System.currentTimeMillis() - startedAt;
                log.info(
                        "Completed chat model stream userId={} sessionId={} messageId={} clientCode={} modelCode={} finishReason={} totalTokens={} elapsedMs={} contentLength={}",
                        userId,
                        session.getId(),
                        assistantMessage.getId(),
                        clientCode,
                        modelResponse.getModelCode(),
                        modelResponse.getFinishReason(),
                        defaultInt(modelResponse.getTotalTokens()),
                        elapsedMs,
                        assistantMessage.getContent() == null ? 0 : assistantMessage.getContent().length()
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
                boolean clientInterrupted = isClientStreamInterrupted(exception);
                boolean recoverableContent = this.hasRecoverableContent(contentBuilder, reasoningBuilder);
                long elapsedMs = System.currentTimeMillis() - startedAt;
                try {
                    if (clientInterrupted) {
                        this.persistInterruptedMessage(assistantMessage, contentBuilder.toString(), reasoningBuilder.toString());
                    } else if (recoverableContent) {
                        this.persistInterruptedMessage(assistantMessage, contentBuilder.toString(), reasoningBuilder.toString());
                    } else {
                        this.persistFailedMessage(assistantMessage);
                    }

                    if (!clientInterrupted && shouldPersistFailureUsage(exception)) {
                        persistFailureUsage(userId, session, model, modelRequest, assistantMessage, exception);
                    }

                    if (!clientInterrupted && !recoverableContent) {
                        ChatStreamErrorEvent errorEvent = new ChatStreamErrorEvent();
                        errorEvent.setMessageId(assistantMessage.getId());
                        errorEvent.setErrorCode("CHAT_STREAM_ERROR");
                        errorEvent.setErrorMessage(exception.getMessage());
                        emitter.send(SseEmitter.event().name("message_error").data(errorEvent));
                    }
                } catch (Exception finalizeException) {
                    log.warn("Failed to finalize interrupted chat stream for messageId={}", assistantMessage.getId(), finalizeException);
                }
                if (clientInterrupted) {
                    log.warn(
                            "Chat model stream interrupted by client userId={} sessionId={} messageId={} clientCode={} modelCode={} elapsedMs={} contentLength={}",
                            userId,
                            session.getId(),
                            assistantMessage.getId(),
                            clientCode,
                            modelRequest.getModelCode(),
                            elapsedMs,
                            contentBuilder.length()
                    );
                } else if (recoverableContent) {
                    log.warn(
                            "Chat model stream interrupted after partial content userId={} sessionId={} messageId={} clientCode={} modelCode={} elapsedMs={} contentLength={} reasoningLength={} errorType={} errorCode={} httpStatus={}",
                            userId,
                            session.getId(),
                            assistantMessage.getId(),
                            clientCode,
                            modelRequest.getModelCode(),
                            elapsedMs,
                            contentBuilder.length(),
                            reasoningBuilder.length(),
                            exception.getClass().getSimpleName(),
                            resolveModelErrorCode(exception),
                            resolveModelHttpStatus(exception)
                    );
                } else {
                    log.error(
                            "Chat model stream failed userId={} sessionId={} messageId={} clientCode={} modelCode={} elapsedMs={} errorCode={} httpStatus={}",
                            userId,
                            session.getId(),
                            assistantMessage.getId(),
                            clientCode,
                            modelRequest.getModelCode(),
                            elapsedMs,
                            resolveModelErrorCode(exception),
                            resolveModelHttpStatus(exception),
                            exception
                    );
                }
                emitter.complete();
            }
        });
    }

    /**
     * 推送流式开始事件，并把本次联网搜索状态同步给前端用于即时回显。
     */
    private void sendStart(Long sessionId, ChatMessageDO assistantMessage, ModelConfigDO model, SseEmitter emitter) throws Exception {
        ChatStreamStartEvent startEvent = new ChatStreamStartEvent();
        startEvent.setSessionId(sessionId);
        startEvent.setMessageId(assistantMessage.getId());
        startEvent.setRole(ChatRoleEnum.ASSISTANT.getCode());
        startEvent.setModelId(model == null ? null : model.getId());
        startEvent.setModelCode(model == null ? null : model.getModelCode());
        startEvent.setModelName(model == null ? null : model.getModelName());
        // 新消息在刷新列表前也需要知道联网状态，因此从占位消息 metadata 解析并放进 start event。
        startEvent.setDeepThinkingEnabled(this.metadataBoolean(this.parseMetadataPayload(assistantMessage.getMetadata()).get("deepThinkingEnabled")));
        WebSearchContext webSearchContext = parseWebSearchContext(assistantMessage.getMetadata());
        if (webSearchContext != null) {
            startEvent.setWebSearchEnabled(webSearchContext.isEnabled());
            startEvent.setWebSearchExecuted(webSearchContext.isExecuted());
            startEvent.setWebSearchStatus(webSearchContext.getStatus());
        }
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

    /**
     * 当会话仍是默认标题且当前消息是首条用户消息时，自动用用户消息生成会话标题。
     */
    private void autoUpdateDefaultSessionTitle(ChatSessionDO session, String userContent, int userSeqNo) {
        if (session == null || userSeqNo != 1 || !isDefaultSessionTitle(session.getTitle()) || !StringUtils.hasText(userContent)) {
            return;
        }

        String autoTitle = buildAutoSessionTitle(userContent);
        if (!StringUtils.hasText(autoTitle)) {
            return;
        }

        LambdaUpdateWrapper<ChatSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ChatSessionDO::getId, session.getId())
                .in(ChatSessionDO::getTitle, DEFAULT_SESSION_TITLES)
                .set(ChatSessionDO::getTitle, autoTitle);
        int updatedRows = chatSessionMapper.update(null, wrapper);
        if (updatedRows > 0) {
            session.setTitle(autoTitle);
            log.info("Auto updated chat session title sessionId={} titleLength={}", session.getId(), autoTitle.length());
        }
    }

    /**
     * 判断会话标题是否仍为系统默认值，非默认标题视为用户或历史逻辑已经明确命名。
     */
    private boolean isDefaultSessionTitle(String title) {
        return StringUtils.hasText(title) && DEFAULT_SESSION_TITLES.contains(title.trim());
    }

    /**
     * 从用户首条消息生成短标题，保留原意但裁剪过长内容。
     */
    private String buildAutoSessionTitle(String content) {
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= AUTO_SESSION_TITLE_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, AUTO_SESSION_TITLE_MAX_LENGTH) + "...";
    }

    private ChatMessageDO buildUserMessage(
            Long userId,
            Long sessionId,
            String content,
            List<FileAssetItemVO> attachments,
            int seqNo
    ) {
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
        entity.setMetadata(buildUserMessageMetadata(attachments));
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

    /**
     * 构造普通发送场景的模型请求，并补齐模型所属供应商上下文。
     */
    private ChatModelRequest buildModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String finalPrompt,
            String requestModeCode,
            Boolean enableDeepThinking,
            List<String> userImageUrls
    ) {
        return this.buildModelRequest(
                session,
                model,
                this.resolveProvider(model),
                userContent,
                finalPrompt,
                requestModeCode,
                enableDeepThinking,
                userImageUrls,
                this.loadContextMessages(session.getId(), CONTEXT_MESSAGE_LIMIT)
        );
    }

    /**
     * 构造继续生成场景的模型请求，并复用原中断回答之前的上下文。
     */
    private ChatModelRequest buildResumeModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            String userContent,
            String basePrompt,
            String requestModeCode,
            Boolean enableDeepThinking,
            List<String> userImageUrls,
            ChatMessageDO interruptedAssistantMessage
    ) {
        String resumePrompt = this.buildResumePrompt(basePrompt, interruptedAssistantMessage.getContent());
        return this.buildModelRequest(
                session,
                model,
                this.resolveProvider(model),
                userContent,
                resumePrompt,
                requestModeCode,
                enableDeepThinking,
                userImageUrls,
                this.loadContextMessagesBeforeSeqNo(session.getId(), interruptedAssistantMessage.getSeqNo(), CONTEXT_MESSAGE_LIMIT)
        );
    }

    /**
     * 把会话、模型、供应商和上下文消息合并成统一模型请求对象。
     */
    private ChatModelRequest buildModelRequest(
            ChatSessionDO session,
            ModelConfigDO model,
            ModelProviderDO provider,
            String userContent,
            String finalPrompt,
            String requestModeCode,
            Boolean enableDeepThinking,
            List<String> userImageUrls,
            List<ChatModelMessage> contextMessages
    ) {
        ChatModelRequest request = new ChatModelRequest();
        request.setSessionId(session.getId());
        request.setModelId(model == null ? null : model.getId());
        request.setModelCode(model == null ? null : model.getModelCode());
        request.setModelName(model == null ? null : model.getModelName());
        // provider 信息由服务端按模型配置解析，前端只传 modelId，避免客户端伪造供应商路由。
        request.setProviderId(provider == null ? null : provider.getId());
        request.setProviderCode(provider == null ? null : provider.getProviderCode());
        request.setProviderName(provider == null ? null : provider.getProviderName());
        request.setProviderBaseUrl(provider == null ? null : provider.getBaseUrl());
        request.setModeCode(requestModeCode != null ? requestModeCode : session.getModeCode());
        request.setEnableDeepThinking(enableDeepThinking);
        request.setSystemPrompt(finalPrompt);
        request.setUserContent(userContent);
        request.setUserImageUrls(userImageUrls);
        request.setMessages(contextMessages);
        return request;
    }

    /**
     * 统一折算模型原生思考开关：显式字段优先，其次按请求 mode，再回落到会话 mode。
     */
    private Boolean resolveEnableDeepThinking(Boolean explicitEnableDeepThinking, String requestModeCode, String sessionModeCode) {
        if (explicitEnableDeepThinking != null) {
            return explicitEnableDeepThinking;
        }
        String effectiveModeCode = StringUtils.hasText(requestModeCode) ? requestModeCode : sessionModeCode;
        return ChatModeEnum.EXPERT == ChatModeEnum.fromCodeOrDefault(effectiveModeCode);
    }

    /**
     * 根据模型配置读取启用供应商，确保后续客户端注册表能按 providerCode 选择真实适配器。
     */
    private ModelProviderDO resolveProvider(ModelConfigDO model) {
        if (model == null || model.getProviderId() == null) {
            return null;
        }

        ModelProviderDO provider = this.modelProviderMapper.selectById(model.getProviderId());
        if (provider == null || !Objects.equals(provider.getStatus(), 1)) {
            throw new BizException(ErrorCode.CHAT_MODEL_NOT_FOUND, "模型供应商不存在或未启用");
        }
        return provider;
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

    /**
     * 将搜索摘要追加到系统提示词中，让模型基于同一份联网资料回答。
     */
    private String appendWebSearchPrompt(String basePrompt, WebSearchContext webSearchContext) {
        if (webSearchContext == null
                || !webSearchContext.isExecuted()
                || !StringUtils.hasText(webSearchContext.getSummary())) {
            return basePrompt;
        }

        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(basePrompt)) {
            prompt.append(basePrompt.trim()).append("\n\n");
        }
        // 单独标出搜索资料边界，降低模型把来源文本和用户问题混在一起的概率。
        prompt.append("联网搜索参考资料：\n");
        prompt.append("以下资料来自本次联网搜索。回答时请优先参考这些资料；如果资料不足或互相矛盾，请直接说明限制，不要编造来源。\n\n");
        prompt.append(webSearchContext.getSummary().trim());
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
            StringBuilder reasoningBuilder,
            long[] lastPersistedAt,
            int[] lastPersistedLength
    ) {
        if (chunk == null || chunk.isDone()) {
            return;
        }
        if (StringUtils.hasText(chunk.getReasoningDelta())) {
            reasoningBuilder.append(chunk.getReasoningDelta());
            ChatStreamReasoningDeltaEvent reasoningDeltaEvent = new ChatStreamReasoningDeltaEvent();
            reasoningDeltaEvent.setMessageId(assistantMessage.getId());
            reasoningDeltaEvent.setReasoningDelta(chunk.getReasoningDelta());
            try {
                emitter.send(SseEmitter.event().name("message_reasoning_delta").data(reasoningDeltaEvent));
            } catch (Exception exception) {
                throw new ChatStreamInterruptedException("CLIENT_STREAM_INTERRUPTED", exception);
            }
        }
        if (StringUtils.hasText(chunk.getDelta())) {
            contentBuilder.append(chunk.getDelta());
            this.persistPartialContentIfNeeded(assistantMessage, contentBuilder, lastPersistedAt, lastPersistedLength);
            ChatStreamDeltaEvent deltaEvent = new ChatStreamDeltaEvent();
            deltaEvent.setMessageId(assistantMessage.getId());
            deltaEvent.setDelta(chunk.getDelta());
            try {
                emitter.send(SseEmitter.event().name("message_delta").data(deltaEvent));
            } catch (Exception exception) {
                throw new ChatStreamInterruptedException("CLIENT_STREAM_INTERRUPTED", exception);
            }
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

    /**
     * 只要正文或思考过程任一存在，就认为这条消息值得保留为可继续生成的中断态。
     */
    private boolean hasRecoverableContent(StringBuilder contentBuilder, StringBuilder reasoningBuilder) {
        return (contentBuilder != null && StringUtils.hasText(contentBuilder.toString()))
                || (reasoningBuilder != null && StringUtils.hasText(reasoningBuilder.toString()));
    }

    /**
     * 将中断消息保留为可继续生成状态，同时把已经拿到的思考过程合并回 metadata。
     */
    private void persistInterruptedMessage(ChatMessageDO assistantMessage, String content, String reasoningContent) {
        if (assistantMessage.getId() == null) {
            return;
        }
        assistantMessage.setContent(content);
        assistantMessage.setMetadata(this.mergeAssistantMessageMetadata(assistantMessage.getMetadata(), reasoningContent));
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

    private String resolveModelErrorCode(Exception exception) {
        if (exception instanceof ChatModelClientException clientException) {
            return clientException.getErrorCode();
        }
        return exception.getClass().getSimpleName();
    }

    private Integer resolveModelHttpStatus(Exception exception) {
        if (exception instanceof ChatModelClientException clientException) {
            return clientException.getHttpStatus();
        }
        return null;
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
        if (exception instanceof ChatModelClientException) {
            return ((ChatModelClientException) exception).getHttpStatus();
        }
        return null;
    }

    private String resolveErrorCode(Exception exception) {
        if (exception instanceof ChatModelClientException) {
            return ((ChatModelClientException) exception).getErrorCode();
        }
        if (exception instanceof BizException) {
            return ((BizException) exception).getMessage();
        }
        return "CHAT_STREAM_ERROR";
    }

    private String resolveErrorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private String resolveFailureResponsePayload(Exception exception) {
        if (exception instanceof ChatModelClientException) {
            String responsePayload = truncate(((ChatModelClientException) exception).getResponsePayload(), 1000);
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
        item.setReasoningContent(this.loadPersistedReasoningContent(message.getMetadata()));
        item.setDeepThinkingEnabled(this.loadPersistedDeepThinkingEnabled(message.getMetadata()));
        item.setContentFormat(message.getContentFormat());
        item.setSeqNo(message.getSeqNo());
        item.setModelId(message.getModelId());
        item.setFinishReason(message.getFinishReason());
        item.setStatus(message.getStatus());
        item.setPromptTokens(message.getPromptTokens());
        item.setCompletionTokens(message.getCompletionTokens());
        item.setTotalTokens(message.getTotalTokens());
        item.setAttachments(parseMessageAttachments(message.getMetadata()));
        // 只把列表展示需要的搜索状态透出，完整搜索结果仍保留在 metadata 里。
        WebSearchContext webSearchContext = parseWebSearchContext(message.getMetadata());
        if (webSearchContext != null) {
            item.setWebSearchEnabled(webSearchContext.isEnabled());
            item.setWebSearchExecuted(webSearchContext.isExecuted());
            item.setWebSearchStatus(webSearchContext.getStatus());
        }
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

    /**
     * 将思考过程追加到 assistant metadata 中，避免刷新后丢失。
     */
    private String mergeAssistantMessageMetadata(String metadata, String reasoningContent) {
        Map<String, Object> payload = this.parseMetadataPayload(metadata);
        if (!StringUtils.hasText(reasoningContent)) {
            return payload.isEmpty() ? null : this.toJsonSafely(payload);
        }
        payload.put("reasoningContent", reasoningContent);
        return this.toJsonSafely(payload);
    }

    private String buildUserMessageMetadata(List<FileAssetItemVO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("attachments", attachments);
        return toJsonSafely(metadata);
    }

    /**
     * 构造 assistant 消息扩展元数据，持久化搜索模式、规则、摘要和来源列表。
     */
    private String buildAssistantMessageMetadata(WebSearchContext webSearchContext) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (webSearchContext == null) {
            return null;
        }
        // 状态字段用于轻量回显；summary/results 用于继续生成时复用上下文。
        metadata.put("webSearchEnabled", webSearchContext.isEnabled());
        metadata.put("webSearchExecuted", webSearchContext.isExecuted());
        metadata.put("webSearchStatus", webSearchContext.getStatus());
        metadata.put("webSearchMode", webSearchContext.getMode());
        metadata.put("webSearchRuleId", webSearchContext.getRuleId());
        metadata.put("webSearchRuleLabel", webSearchContext.getRuleLabel());
        metadata.put("webSearchQuery", webSearchContext.getQuery());
        if (StringUtils.hasText(webSearchContext.getSummary())) {
            metadata.put("webSearchSummary", webSearchContext.getSummary());
        }
        if (webSearchContext.getResults() != null && !webSearchContext.getResults().isEmpty()) {
            metadata.put("webSearchResults", webSearchContext.getResults());
        }
        if (StringUtils.hasText(webSearchContext.getErrorCode())) {
            metadata.put("webSearchErrorCode", webSearchContext.getErrorCode());
        }
        if (StringUtils.hasText(webSearchContext.getErrorMessage())) {
            metadata.put("webSearchErrorMessage", webSearchContext.getErrorMessage());
        }
        return toJsonSafely(metadata);
    }

    /**
     * assistant 占位消息在真正开始流式前就先写入思考模式标记，前端可立刻显示“思考中”占位。
     */
    private String mergeAssistantThinkingMetadata(String metadata, Boolean deepThinkingEnabled) {
        if (deepThinkingEnabled == null) {
            return metadata;
        }
        Map<String, Object> payload = this.parseMetadataPayload(metadata);
        payload.put("deepThinkingEnabled", deepThinkingEnabled);
        return this.toJsonSafely(payload);
    }

    /**
     * 从 assistant metadata 中恢复联网搜索上下文，供列表回显和 regenerate 复用。
     */
    private WebSearchContext parseWebSearchContext(String metadata) {
        try {
            Map<String, Object> payload = this.parseMetadataPayload(metadata);
            if (!payload.containsKey("webSearchEnabled")) {
                return null;
            }

            // metadata 是长期扩展位，解析时按宽松类型处理以兼容历史数据。
            WebSearchContext context = new WebSearchContext();
            context.setEnabled(metadataBoolean(payload.get("webSearchEnabled")));
            context.setExecuted(metadataBoolean(payload.get("webSearchExecuted")));
            context.setStatus(metadataText(payload.get("webSearchStatus")));
            context.setMode(metadataText(payload.get("webSearchMode")));
            context.setRuleId(metadataText(payload.get("webSearchRuleId")));
            context.setRuleLabel(metadataText(payload.get("webSearchRuleLabel")));
            context.setQuery(metadataText(payload.get("webSearchQuery")));
            context.setSummary(metadataText(payload.get("webSearchSummary")));
            context.setErrorCode(metadataText(payload.get("webSearchErrorCode")));
            context.setErrorMessage(metadataText(payload.get("webSearchErrorMessage")));

            Object results = payload.get("webSearchResults");
            if (results != null) {
                // 结果列表完整保存在 metadata 中，继续生成时不再触发新的外部搜索。
                context.setResults(objectMapper.convertValue(results, new TypeReference<List<WebSearchResultItem>>() {}));
            }
            return context;
        } catch (Exception exception) {
            log.warn("Failed to parse chat message web search metadata", exception);
            return null;
        }
    }

    private List<FileAssetItemVO> parseMessageAttachments(String metadata) {
        try {
            Map<String, Object> payload = this.parseMetadataPayload(metadata);
            Object attachments = payload.get("attachments");
            if (attachments == null) {
                return List.of();
            }
            return objectMapper.convertValue(attachments, new TypeReference<List<FileAssetItemVO>>() {});
        } catch (Exception exception) {
            log.warn("Failed to parse chat message attachments metadata", exception);
            return List.of();
        }
    }

    /**
     * 从消息 metadata 中恢复完整思考过程，供刷新回显使用。
     */
    private String loadPersistedReasoningContent(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return "";
        }
        try {
            Map<String, Object> payload = this.parseMetadataPayload(metadata);
            return StringUtils.hasText(this.metadataText(payload.get("reasoningContent")))
                    ? this.metadataText(payload.get("reasoningContent"))
                    : "";
        } catch (Exception exception) {
            log.warn("Failed to parse chat message reasoning metadata", exception);
            return "";
        }
    }

    /**
     * 从消息 metadata 中恢复是否开启过原生思考模式，供前端在首个 reasoning chunk 到达前显示占位。
     */
    private Boolean loadPersistedDeepThinkingEnabled(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return null;
        }
        Map<String, Object> payload = this.parseMetadataPayload(metadata);
        if (!payload.containsKey("deepThinkingEnabled")) {
            return null;
        }
        return this.metadataBoolean(payload.get("deepThinkingEnabled"));
    }

    /**
     * 宽松解析消息 metadata，兼容历史空值和后续新增字段。
     */
    private Map<String, Object> parseMetadataPayload(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return new LinkedHashMap<>();
        }
        try {
            return this.objectMapper.readValue(metadata, new TypeReference<>() {});
        } catch (Exception exception) {
            log.warn("Failed to parse chat message metadata payload", exception);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 宽松解析 metadata 中的布尔值，兼容 JSON boolean 和字符串形式。
     */
    private boolean metadataBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 宽松解析 metadata 中的文本值，空字符串统一折算为 null。
     */
    private String metadataText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : null;
    }
}
