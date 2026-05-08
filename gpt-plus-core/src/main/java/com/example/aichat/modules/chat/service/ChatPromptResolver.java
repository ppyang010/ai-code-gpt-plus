package com.example.aichat.modules.chat.service;

public interface ChatPromptResolver {

    String resolveSystemPrompt(String modeCode, String sessionPrompt, String requestPrompt);
}
