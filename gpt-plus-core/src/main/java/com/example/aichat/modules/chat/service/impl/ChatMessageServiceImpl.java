package com.example.aichat.modules.chat.service.impl;

import com.example.aichat.common.enums.ChatModeEnum;
import com.example.aichat.common.enums.ChatRoleEnum;
import com.example.aichat.common.enums.MessageStatusEnum;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.ChatModelClient;
import com.example.aichat.infrastructure.ai.ChatModelClientRegistry;
import com.example.aichat.infrastructure.ai.ChatModelMessage;
import com.example.aichat.infrastructure.ai.ChatModelRequest;
import com.example.aichat.infrastructure.ai.ChatModelResponse;
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
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final int SESSION_STATUS_ACTIVE = 1;

    private final ChatPromptResolver chatPromptResolver;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final ChatModelClientRegistry chatModelClientRegistry;

    public ChatMessageServiceImpl(
            ChatPromptResolver chatPromptResolver,
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ModelConfigMapper modelConfigMapper,
            ChatModelClientRegistry chatModelClientRegistry
    ) {
        this.chatPromptResolver = chatPromptResolver;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.chatModelClientRegistry = chatModelClientRegistry;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(Long userId, ChatMessageSendRequest request, SseEmitter emitter) {
        if (request.getSessionId() == null) {
            throw new BizException(400, "CHAT_SESSION_NOT_FOUND");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(400, "CHAT_MESSAGE_EMPTY");
        }

        ChatSessionDO session = getActiveSession(userId, request.getSessionId());
        ModelConfigDO model = resolveModel(session, request.getModelId());

        String finalPrompt = chatPromptResolver.resolveSystemPrompt(
                request.getModeCode() != null ? request.getModeCode() : session.getModeCode(),
                session.getSystemPrompt(),
                request.getSystemPrompt()
        );

        ChatModelRequest modelRequest = buildModelRequest(session, model, request.getContent(), finalPrompt, request.getModeCode());

        int nextSeqNo = nextSeqNo(session.getId());
        ChatMessageDO userMessage = buildUserMessage(userId, session.getId(), request.getContent(), nextSeqNo);
        chatMessageMapper.insert(userMessage);

        streamMockAnswer(
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
        throw new BizException(501, "CHAT_REGENERATE_NOT_IMPLEMENTED");
    }

    private void streamMockAnswer(
            Long userId,
            ChatSessionDO session,
            ModelConfigDO model,
            ChatModelRequest modelRequest,
            int assistantSeqNo,
            SseEmitter emitter
    ) {
        CompletableFuture.runAsync(() -> {
            ChatMessageDO assistantMessage = buildAssistantPlaceholder(userId, session.getId(), model, assistantSeqNo);
            try {
                chatMessageMapper.insert(assistantMessage);
                sendStart(session.getId(), assistantMessage.getId(), model, emitter);

                ChatModelClient chatModelClient = chatModelClientRegistry.resolve(modelRequest);
                ChatModelResponse modelResponse = chatModelClient.streamChat(modelRequest, delta -> emitDelta(emitter, assistantMessage.getId(), delta));

                assistantMessage.setContent(modelResponse.getContent());
                assistantMessage.setFinishReason(modelResponse.getFinishReason());
                assistantMessage.setStatus(MessageStatusEnum.NORMAL.getCode());
                assistantMessage.setPromptTokens(defaultInt(modelResponse.getPromptTokens()));
                assistantMessage.setCompletionTokens(defaultInt(modelResponse.getCompletionTokens()));
                assistantMessage.setTotalTokens(defaultInt(modelResponse.getTotalTokens()));
                chatMessageMapper.updateById(assistantMessage);
                chatSessionMapper.updateLastMessageAt(session.getId(), LocalDateTime.now());

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
                    assistantMessage.setStatus(MessageStatusEnum.FAILED.getCode());
                    assistantMessage.setFinishReason("error");
                    if (assistantMessage.getId() != null) {
                        chatMessageMapper.updateById(assistantMessage);
                    }
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
            throw new BizException(404, "CHAT_SESSION_NOT_FOUND");
        }
        return session;
    }

    private ModelConfigDO resolveModel(ChatSessionDO session, Long requestModelId) {
        Long modelId = requestModelId != null ? requestModelId : session.getDefaultModelId();
        if (modelId == null) {
            return null;
        }
        ModelConfigDO model = modelConfigMapper.selectEnabledById(modelId);
        if (model == null) {
            throw new BizException(404, "CHAT_MODEL_NOT_FOUND");
        }
        return model;
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
        return chatMessageMapper.selectLatestMessages(sessionId, 10).stream()
                .sorted((left, right) -> Integer.compare(left.getSeqNo(), right.getSeqNo()))
                .map(message -> new ChatModelMessage(message.getRole(), message.getContent()))
                .collect(Collectors.toList());
    }

    private void emitDelta(SseEmitter emitter, Long messageId, String delta) {
        if (delta == null) {
            return;
        }
        ChatStreamDeltaEvent deltaEvent = new ChatStreamDeltaEvent();
        deltaEvent.setMessageId(messageId);
        deltaEvent.setDelta(delta);
        try {
            emitter.send(SseEmitter.event().name("message_delta").data(deltaEvent));
        } catch (Exception exception) {
            throw new BizException(500, "CHAT_STREAM_ERROR");
        }
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
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
