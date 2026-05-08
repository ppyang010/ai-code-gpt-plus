package com.example.aichat.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;

public class ChatModelRequest {

    private Long sessionId;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String modeCode;
    private String systemPrompt;
    private String userContent;
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

    public String getModeCode() {
        return modeCode;
    }

    public void setModeCode(String modeCode) {
        this.modeCode = modeCode;
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

    public List<ChatModelMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatModelMessage> messages) {
        this.messages = messages;
    }
}
