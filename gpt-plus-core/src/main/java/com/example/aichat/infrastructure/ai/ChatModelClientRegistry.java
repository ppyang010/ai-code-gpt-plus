package com.example.aichat.infrastructure.ai;

import com.example.aichat.common.exception.BizException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatModelClientRegistry {

    private final List<ChatModelClient> clients;

    public ChatModelClientRegistry(List<ChatModelClient> clients) {
        this.clients = clients;
    }

    public ChatModelClient resolve(ChatModelRequest request) {
        ChatModelClient fallback = null;
        for (ChatModelClient client : clients) {
            if ("mock".equals(client.clientCode())) {
                fallback = client;
                continue;
            }
            if (client.supports(request)) {
                return client;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new BizException(500, "CHAT_MODEL_CLIENT_NOT_FOUND");
    }
}
