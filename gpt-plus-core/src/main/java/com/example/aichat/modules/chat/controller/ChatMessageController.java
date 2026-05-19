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

/**
 * 聊天消息接口，负责会话内消息查询、发送和重新生成。
 */
@RestController
@RequestMapping("/api/chat/message")
@Validated
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    public ChatMessageController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    /**
     * 查询指定会话的消息列表。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param sessionId 会话 ID
     * @return 会话消息列表
     */
    @GetMapping("/list")
    public CommonResponse<ChatMessageListVO> listMessages(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Positive(message = "must be greater than 0") @RequestParam("sessionId") Long sessionId
    ) {
        return CommonResponse.success(chatMessageService.listMessages(resolveUserId(userId), sessionId));
    }

    /**
     * 发送用户消息，并通过 SSE 持续返回模型回复分片。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param request 消息发送请求
     * @return 用于推送模型流式响应的 SSE emitter
     */
    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatMessageSendRequest request
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        chatMessageService.sendMessage(resolveUserId(userId), request, emitter);
        return emitter;
    }

    /**
     * 基于已有消息重新生成模型回复，并通过 SSE 返回新的回复分片。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param request 重新生成请求
     * @return 用于推送模型流式响应的 SSE emitter
     */
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateMessage(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatMessageRegenerateRequest request
    ) {
        SseEmitter emitter = new SseEmitter(0L);
        chatMessageService.regenerateMessage(resolveUserId(userId), request, emitter);
        return emitter;
    }

    /**
     * 解析请求用户；当前项目还未接入登录态，未传请求头时固定落到本地默认用户。
     */
    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
