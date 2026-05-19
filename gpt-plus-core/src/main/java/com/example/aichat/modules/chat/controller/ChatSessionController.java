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

/**
 * 聊天会话接口，提供会话列表、创建、改名和删除能力。
 */
@RestController
@RequestMapping("/api/chat/session")
@Validated
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    /**
     * 分页查询当前用户的聊天会话列表。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param pageNo 页码，未传时由服务层使用默认值
     * @param pageSize 每页数量，最大 100，未传时由服务层使用默认值
     * @return 分页后的会话列表
     */
    @GetMapping("/list")
    public CommonResponse<PageResponse<ChatSessionListItemVO>> listSessions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Positive(message = "must be greater than 0") @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @Min(value = 1, message = "must be greater than 0") @Max(value = 100, message = "must be at most 100")
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        return CommonResponse.success(chatSessionService.listUserSessions(resolveUserId(userId), pageNo, pageSize));
    }

    /**
     * 创建新的聊天会话。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param request 会话创建请求
     * @return 新建会话信息
     */
    @PostMapping("/create")
    public CommonResponse<ChatSessionCreateVO> createSession(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionCreateRequest request
    ) {
        return CommonResponse.success(chatSessionService.createSession(resolveUserId(userId), request));
    }

    /**
     * 修改会话标题。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param request 会话标题更新请求
     * @return 是否修改成功
     */
    @PostMapping("/update-title")
    public CommonResponse<Boolean> updateTitle(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionUpdateTitleRequest request
    ) {
        chatSessionService.updateTitle(resolveUserId(userId), request);
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 删除指定聊天会话。
     *
     * @param userId 请求头中的用户 ID，本地调试未传时使用默认用户
     * @param request 会话删除请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    public CommonResponse<Boolean> deleteSession(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChatSessionDeleteRequest request
    ) {
        chatSessionService.deleteSession(resolveUserId(userId), request.getSessionId());
        return CommonResponse.success(Boolean.TRUE);
    }

    /**
     * 解析请求用户；当前项目还未接入登录态，未传请求头时固定落到本地默认用户。
     */
    private Long resolveUserId(Long userId) {
        return userId != null ? userId : 1L;
    }
}
