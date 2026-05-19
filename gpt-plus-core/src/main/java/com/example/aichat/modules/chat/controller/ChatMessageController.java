package com.example.aichat.modules.chat.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.modules.chat.dto.ChatMessageRegenerateRequest;
import com.example.aichat.modules.chat.dto.ChatMessageSendRequest;
import com.example.aichat.modules.chat.service.ChatMessageService;
import com.example.aichat.modules.chat.vo.ChatMessageListVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat/message")
@Validated
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    @GetMapping("/list")
    public CommonResponse<ChatMessageListVO> listMessages(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Positive(message = "must be greater than 0") @RequestParam("sessionId") Long sessionId
    ) {
        return CommonResponse.success(chatMessageService.listMessages(resolveUserId(userId), sessionId));
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        chatMessageService.sendMessage(resolveUserId(userId), request, emitter);
        return emitter;
    }

    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateMessage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatMessageRegenerateRequest request
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        chatMessageService.regenerateMessage(resolveUserId(userId), request, emitter);
        return emitter;
    }

    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
