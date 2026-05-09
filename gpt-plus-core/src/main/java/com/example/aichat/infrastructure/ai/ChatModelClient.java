package com.example.aichat.infrastructure.ai;

import java.util.function.Consumer;

public interface ChatModelClient {

    boolean supports(ChatModelRequest request);

    ChatModelResponse streamChat(ChatModelRequest request, Consumer<ChatModelStreamChunk> chunkConsumer);

    String clientCode();
}
