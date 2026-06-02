package com.example.aichat.infrastructure.ai;

/**
 * 模型流式输出分片。
 *
 * <p>正文增量和思考过程增量共用同一个抽象，便于聊天服务统一分发为不同 SSE 事件。</p>
 */
public class ChatModelStreamChunk {

    /** assistant 最终回答正文的增量片段。 */
    private final String delta;
    /** 模型原生思考过程的增量片段。 */
    private final String reasoningDelta;
    /** 是否为流式结束标记。 */
    private final boolean done;

    public ChatModelStreamChunk(String delta, boolean done) {
        this(delta, null, done);
    }

    public ChatModelStreamChunk(String delta, String reasoningDelta, boolean done) {
        this.delta = delta;
        this.reasoningDelta = reasoningDelta;
        this.done = done;
    }

    public String getDelta() {
        return delta;
    }

    public String getReasoningDelta() {
        return reasoningDelta;
    }

    public boolean isDone() {
        return done;
    }
}
