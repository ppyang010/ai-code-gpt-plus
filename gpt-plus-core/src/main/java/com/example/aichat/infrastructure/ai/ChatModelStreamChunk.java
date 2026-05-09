package com.example.aichat.infrastructure.ai;

public class ChatModelStreamChunk {

    private final String delta;
    private final boolean done;

    public ChatModelStreamChunk(String delta, boolean done) {
        this.delta = delta;
        this.done = done;
    }

    public String getDelta() {
        return delta;
    }

    public boolean isDone() {
        return done;
    }
}
