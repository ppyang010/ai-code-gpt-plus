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
    CHAT_MESSAGE_NOT_FOUND(404, "消息不存在"),
    CHAT_MESSAGE_ALREADY_GENERATING(409, "当前消息正在生成中，请稍后再试"),
    CHAT_MESSAGE_NOT_INTERRUPTIBLE(409, "当前消息不支持继续生成"),
    CHAT_ATTACHMENT_NOT_FOUND(404, "图片附件不存在或不可用"),
    CHAT_STREAM_ERROR(500, "流式输出异常"),
    CHAT_REGENERATE_NOT_IMPLEMENTED(501, "重新生成能力暂未接入"),
    FILE_IMAGE_EMPTY(400, "请先选择要上传的图片"),
    FILE_IMAGE_TOO_LARGE(400, "图片大小不能超过 5MB"),
    FILE_IMAGE_TYPE_NOT_SUPPORTED(400, "仅支持上传 jpg、jpeg、png、webp 图片"),
    FILE_ASSET_NOT_FOUND(404, "图片附件不存在"),
    FILE_UPLOAD_FAILED(500, "图片上传失败，请稍后重试"),
    DEEPSEEK_API_KEY_NOT_CONFIGURED(503, "DeepSeek API Key 未配置"),
    // 后端判定必须联网但搜索开关、API Key 或客户端配置缺失。
    WEB_SEARCH_NOT_CONFIGURED(503, "联网搜索未配置"),
    // 搜索供应商请求失败时统一返回，具体供应商错误只写日志。
    WEB_SEARCH_FAILED(502, "联网搜索失败，请稍后重试"),
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
