package com.example.aichat.modules.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.common.enums.ChatModeEnum;
import com.example.aichat.common.enums.ErrorCode;
import com.example.aichat.common.exception.BizException;
import com.example.aichat.infrastructure.ai.deepseek.DeepSeekProperties;
import com.example.aichat.modules.chat.dto.ChatSessionCreateRequest;
import com.example.aichat.modules.chat.dto.ChatSessionUpdateTitleRequest;
import com.example.aichat.modules.chat.entity.ChatMessageDO;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import com.example.aichat.modules.chat.mapper.ChatMessageMapper;
import com.example.aichat.modules.chat.mapper.ChatSessionMapper;
import com.example.aichat.modules.chat.service.ChatSessionService;
import com.example.aichat.modules.chat.vo.ChatSessionCreateVO;
import com.example.aichat.modules.chat.vo.ChatSessionListItemVO;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import com.example.aichat.modules.model.mapper.ModelConfigMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private static final int SESSION_STATUS_ACTIVE = 1;
    private static final int SESSION_STATUS_DELETED = 0;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelConfigMapper modelConfigMapper;
    private final DeepSeekProperties deepSeekProperties;

    public ChatSessionServiceImpl(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ModelConfigMapper modelConfigMapper,
            DeepSeekProperties deepSeekProperties
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.modelConfigMapper = modelConfigMapper;
        this.deepSeekProperties = deepSeekProperties;
    }

    @Override
    public PageResponse<ChatSessionListItemVO> listUserSessions(Long userId, Integer pageNo, Integer pageSize) {
        int normalizedPageNo = normalizePageNo(pageNo);
        int normalizedPageSize = normalizePageSize(pageSize);
        long offset = (long) (normalizedPageNo - 1) * normalizedPageSize;

        List<ChatSessionDO> sessions = chatSessionMapper.selectPageByUserId(userId, offset, normalizedPageSize);
        Long total = chatSessionMapper.countByUserId(userId);

        Set<Long> modelIds = sessions.stream()
                .map(ChatSessionDO::getDefaultModelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 列表页批量加载模型信息，避免每个会话单独查一次模型表。
        Map<Long, ModelConfigDO> modelMap = loadModelMap(modelIds);

        PageResponse<ChatSessionListItemVO> response = new PageResponse<>();
        response.setList(sessions.stream().map(session -> toSessionListItem(session, modelMap)).collect(Collectors.toList()));
        response.setTotal(total == null ? 0L : total);
        response.setPageNo(normalizedPageNo);
        response.setPageSize(normalizedPageSize);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSessionCreateVO createSession(Long userId, ChatSessionCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String modeCode = ChatModeEnum.fromCodeOrDefault(request.getModeCode()).getCode();

        Long defaultModelId = resolveDefaultModelId(request.getDefaultModelId());

        ChatSessionDO entity = new ChatSessionDO();
        entity.setUserId(userId);
        entity.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle().trim() : "New Chat");
        entity.setModeCode(modeCode);
        entity.setDefaultModelId(defaultModelId);
        entity.setSystemPrompt(request.getSystemPrompt());
        entity.setLastMessageAt(now);
        entity.setStatus(SESSION_STATUS_ACTIVE);
        chatSessionMapper.insert(entity);

        ChatSessionCreateVO response = new ChatSessionCreateVO();
        response.setSessionId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setModeCode(entity.getModeCode());
        response.setDefaultModelId(defaultModelId);
        response.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : now);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(Long userId, ChatSessionUpdateTitleRequest request) {
        if (request.getSessionId() == null) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException(ErrorCode.CHAT_SESSION_TITLE_EMPTY);
        }

        ChatSessionDO session = getActiveSession(userId, request.getSessionId());
        LambdaUpdateWrapper<ChatSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ChatSessionDO::getId, session.getId())
                .set(ChatSessionDO::getTitle, request.getTitle().trim());
        chatSessionMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        ChatSessionDO session = getActiveSession(userId, sessionId);
        LambdaUpdateWrapper<ChatSessionDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ChatSessionDO::getId, session.getId())
                .set(ChatSessionDO::getStatus, SESSION_STATUS_DELETED);
        chatSessionMapper.update(null, wrapper);
    }

    private int normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : pageSize;
    }

    private ChatSessionDO getActiveSession(Long userId, Long sessionId) {
        ChatSessionDO session = chatSessionMapper.selectActiveById(sessionId, userId);
        if (session == null) {
            throw new BizException(ErrorCode.CHAT_SESSION_NOT_FOUND);
        }
        return session;
    }

    private Long resolveDefaultModelId(Long requestModelId) {
        if (requestModelId != null) {
            if (modelConfigMapper.selectEnabledById(requestModelId) == null) {
                throw new BizException(ErrorCode.CHAT_MODEL_NOT_FOUND);
            }
            return requestModelId;
        }

        // 前端可以不传模型，后端用 DeepSeek 默认模型保证创建会话仍然可用。
        ModelConfigDO defaultModel = modelConfigMapper.selectByModelCode(deepSeekProperties.getDefaultModel());
        if (defaultModel == null || !Objects.equals(defaultModel.getStatus(), 1)) {
            throw new BizException(ErrorCode.CHAT_DEFAULT_MODEL_NOT_CONFIGURED);
        }
        return defaultModel.getId();
    }

    private Map<Long, ModelConfigDO> loadModelMap(Set<Long> modelIds) {
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return modelIds.stream()
                .map(modelConfigMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ModelConfigDO::getId, Function.identity(), (left, right) -> left));
    }

    private ChatSessionListItemVO toSessionListItem(ChatSessionDO session, Map<Long, ModelConfigDO> modelMap) {
        ChatSessionListItemVO item = new ChatSessionListItemVO();
        item.setSessionId(session.getId());
        item.setTitle(session.getTitle());
        item.setModeCode(session.getModeCode());
        item.setDefaultModelId(session.getDefaultModelId());
        item.setLastMessageAt(session.getLastMessageAt());
        item.setCreatedAt(session.getCreatedAt());

        ModelConfigDO model = session.getDefaultModelId() == null ? null : modelMap.get(session.getDefaultModelId());
        if (model != null) {
            item.setDefaultModelCode(model.getModelCode());
            item.setDefaultModelName(model.getModelName());
        }

        // 左侧列表只展示最近一条消息摘要，完整消息仍由 message/list 单独加载。
        List<ChatMessageDO> latestMessages = chatMessageMapper.selectLatestMessages(session.getId(), 1);
        if (!latestMessages.isEmpty()) {
            String content = latestMessages.get(0).getContent();
            item.setLastMessagePreview(shorten(content, 50));
        }
        return item;
    }

    private String shorten(String content, int maxLength) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        String value = content.trim();
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
