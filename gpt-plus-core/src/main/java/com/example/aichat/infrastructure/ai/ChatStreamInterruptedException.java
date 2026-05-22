package com.example.aichat.infrastructure.ai;

/**
 * 标记浏览器或 SSE 客户端主动断开连接。
 *
 * <p>这类异常不是模型供应商调用失败，而是流式结果在回推到客户端时被中断，
 * 上层需要把已生成内容落成 INTERRUPTED，而不是记成 provider failure。</p>
 */
public class ChatStreamInterruptedException extends RuntimeException {

    public ChatStreamInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
