package com.example.aichat.modules.chat.service;

public interface WebSearchIntentResolver {

    WebSearchIntentResolution resolve(String webSearchMode, Boolean enableWebSearch, String content);
}
