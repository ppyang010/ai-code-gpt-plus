package com.example.aichat.common.enums;

public enum ErrorCode {

    INVALID_PARAM(400, "请求参数不合法"),
    REQUEST_BODY_INVALID(400, "请求格式不正确"),
    CHAT_SESSION_NOT_FOUND(404, "会话不存在或已删除"),
    CHAT_SESSION_TITLE_EMPTY(400, "会话标题不能为空"),
    CHAT_MODEL_NOT_FOUND(404, "模型不存在或未启用"),
    CHAT_DEFAULT_MODEL_NOT_CONFIGURED(500, "默认模型未配置或未启用"),
    CHAT_MODEL_CLIENT_NOT_FOUND(500, "未找到可用的模型客户端"),
    CHAT_MESSAGE_EMPTY(400, "消息内容不能为空"),
    CHAT_STREAM_ERROR(500, "流式输出异常"),
    CHAT_REGENERATE_NOT_IMPLEMENTED(501, "重新生成能力暂未接入"),
    DEEPSEEK_API_KEY_NOT_CONFIGURED(503, "DeepSeek API Key 未配置"),
    INTERNAL_ERROR(500, "服务开小差了，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
