package com.example.aichat.modules.chat.service;

import com.example.aichat.modules.chat.dto.ChatMessageRegenerateRequest;
import com.example.aichat.modules.chat.dto.ChatMessageSendRequest;
import com.example.aichat.modules.chat.vo.ChatMessageListVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatMessageService {

    ChatMessageListVO listMessages(Long userId, Long sessionId);

    void sendMessage(Long userId, ChatMessageSendRequest request, SseEmitter emitter);

    void regenerateMessage(Long userId, ChatMessageRegenerateRequest request, SseEmitter emitter);
}
