package com.example.aichat.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一模型请求上下文，承载业务层解析后的模型、供应商、提示词和历史消息。
 */
public class ChatModelRequest {

    /** 会话 ID，用于把模型调用和当前聊天上下文关联起来。 */
    private Long sessionId;
    /** 本次请求使用的模型配置 ID，用于调用日志和消息追踪。 */
    private Long modelId;
    /** 本次请求使用的模型编码，真实供应商 payload 中会作为 model 字段。 */
    private String modelCode;
    /** 本次请求使用的模型展示名，用于 SSE 事件和消息列表回显。 */
    private String modelName;
    /** 模型所属供应商 ID，对应 model_provider.id。 */
    private Long providerId;
    /** 模型所属供应商编码，用于从客户端注册表中选择真实供应商适配器。 */
    private String providerCode;
    /** 模型所属供应商名称，用于日志和后续展示扩展。 */
    private String providerName;
    /** 数据库中的供应商基础地址，作为本地密钥配置缺省 base-url 时的兜底。 */
    private String providerBaseUrl;
    /** 当前会话模式编码，模型客户端可按模式生成 mock 或后续扩展差异化参数。 */
    private String modeCode;
    /** 是否开启模型原生思考模式，由 quick / expert 或显式请求字段折算而来。 */
    private Boolean enableDeepThinking;
    /** 后端最终合成的系统提示词，包含模式提示词和可选联网搜索资料。 */
    private String systemPrompt;
    /** 当前用户输入正文，单独传给客户端避免和历史上下文重复。 */
    private String userContent;
    /** 当前用户输入关联的图片附件，转成多模态 `image_url` 内容块后随本次 user message 一起发送。 */
    private List<String> userImageUrls = new ArrayList<>();
    /** 已排序的历史上下文消息，供模型连续理解当前会话。 */
    private List<ChatModelMessage> messages = new ArrayList<>();

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getModelId() {
        return modelId;
    }

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderBaseUrl() {
        return providerBaseUrl;
    }

    public void setProviderBaseUrl(String providerBaseUrl) {
        this.providerBaseUrl = providerBaseUrl;
    }

    public String getModeCode() {
        return modeCode;
    }

    public void setModeCode(String modeCode) {
        this.modeCode = modeCode;
    }

    public Boolean getEnableDeepThinking() {
        return enableDeepThinking;
    }

    public void setEnableDeepThinking(Boolean enableDeepThinking) {
        this.enableDeepThinking = enableDeepThinking;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getUserContent() {
        return userContent;
    }

    public void setUserContent(String userContent) {
        this.userContent = userContent;
    }

    public List<String> getUserImageUrls() {
        return userImageUrls;
    }

    public void setUserImageUrls(List<String> userImageUrls) {
        this.userImageUrls = userImageUrls;
    }

    public List<ChatModelMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatModelMessage> messages) {
        this.messages = messages;
    }
}
