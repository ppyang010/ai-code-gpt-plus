package com.example.aichat.modules.chat.controller;

import com.example.aichat.common.dto.CommonResponse;
import com.example.aichat.common.dto.PageResponse;
import com.example.aichat.modules.chat.dto.ChatSessionCreateRequest;
import com.example.aichat.modules.chat.dto.ChatSessionDeleteRequest;
import com.example.aichat.modules.chat.dto.ChatSessionUpdateTitleRequest;
import com.example.aichat.modules.chat.service.ChatSessionService;
import com.example.aichat.modules.chat.vo.ChatSessionCreateVO;
import com.example.aichat.modules.chat.vo.ChatSessionListItemVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/session")
@Validated
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/list")
    public CommonResponse<PageResponse<ChatSessionListItemVO>> listSessions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Positive(message = "must be greater than 0") @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @Min(value = 1, message = "must be greater than 0") @Max(value = 100, message = "must be at most 100")
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return CommonResponse.success(chatSessionService.listUserSessions(resolveUserId(userId), pageNo, pageSize));
    }

    @PostMapping("/create")
    public CommonResponse<ChatSessionCreateVO> createSession(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionCreateRequest request
    ) {
        return CommonResponse.success(chatSessionService.createSession(resolveUserId(userId), request));
    }

    @PostMapping("/update-title")
    public CommonResponse<Boolean> updateTitle(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionUpdateTitleRequest request
    ) {
        chatSessionService.updateTitle(resolveUserId(userId), request);
        return CommonResponse.success(Boolean.TRUE);
    }

    @PostMapping("/delete")
    public CommonResponse<Boolean> deleteSession(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionDeleteRequest request
    ) {
        chatSessionService.deleteSession(resolveUserId(userId), request.getSessionId());
        return CommonResponse.success(Boolean.TRUE);
    }

    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
