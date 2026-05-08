package com.example.aichat.modules.chat.service;

import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.chat.dto.ChatSessionCreateRequest;
import com.example.aichat.modules.chat.dto.ChatSessionUpdateTitleRequest;
import com.example.aichat.modules.chat.vo.ChatSessionCreateVO;
import com.example.aichat.modules.chat.vo.ChatSessionListItemVO;

public interface ChatSessionService {

    PageResponse<ChatSessionListItemVO> listUserSessions(Long userId, Integer pageNo, Integer pageSize);

    ChatSessionCreateVO createSession(Long userId, ChatSessionCreateRequest request);

    void updateTitle(Long userId, ChatSessionUpdateTitleRequest request);

    void deleteSession(Long userId, Long sessionId);
}
