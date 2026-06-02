# 聊天响应中断后继续流程图

最后更新：2026-05-20

## 说明

本文档用于补充 [chat-response-resume-design.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/chat-response-resume-design.md) 的流程图视角，帮助快速理解“发送消息 -> 流式生成 -> 中断 -> 继续生成”的完整链路。

适用场景：

- 联调前快速对齐前后端职责
- 排查“为什么中断后不能继续生成”
- 新同学快速理解 `send / regenerate / SSE / message status` 关系

## 1. 总览流程

```mermaid
flowchart TD
    A["用户发送消息"] --> B["前端 chatStore.sendMessage"]
    B --> C["POST /api/chat/message/send"]
    C --> D["后端创建 user message"]
    D --> E["后端创建 assistant 占位消息<br/>status=GENERATING"]
    E --> F["调用模型并通过 SSE 推送 delta"]
    F --> G{"流式过程是否正常结束?"}
    G -- "是" --> H["回写完整 content / token<br/>status=NORMAL"]
    H --> I["前端收到 message_end<br/>消息显示完成"]
    G -- "否" --> J{"是否已有部分内容?"}
    J -- "否" --> K["回写失败状态<br/>status=FAILED"]
    K --> L["前端显示失败/重试"]
    J -- "是" --> M["回写部分 content<br/>status=INTERRUPTED"]
    M --> N["前端或刷新后看到中断消息"]
    N --> O["用户点击继续生成"]
    O --> P["POST /api/chat/message/regenerate"]
    P --> Q["后端校验目标 assistant 消息为 INTERRUPTED"]
    Q --> R["构造 continue prompt"]
    R --> S["同一条 assistant 消息改回 GENERATING"]
    S --> T["再次调用模型并继续 SSE 推送"]
    T --> U{"继续生成是否正常结束?"}
    U -- "是" --> V["同一 messageId 继续追加 content<br/>status=NORMAL"]
    U -- "否" --> W["保留已生成部分<br/>status=INTERRUPTED 或 FAILED"]
```

## 2. 首次发送消息时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端 ChatView/chatStore
    participant C as ChatMessageController
    participant S as ChatMessageServiceImpl
    participant DB as MySQL
    participant M as DeepSeek

    U->>F: 输入内容并发送
    F->>C: POST /api/chat/message/send
    C->>S: sendMessage(userId, request, emitter)
    S->>DB: 插入 user message
    S->>DB: 插入 assistant 占位消息(status=GENERATING)
    S-->>F: SSE message_start(messageId)
    S->>M: streamChat(modelRequest)
    loop 增量生成
        M-->>S: delta chunk
        S->>DB: 按阈值回写部分 content
        S-->>F: SSE message_delta
    end
    alt 正常结束
        M-->>S: finishReason / usage
        S->>DB: 回写完整 content / token / status=NORMAL
        S-->>F: SSE message_end
    else 中断或异常
        S->>DB: 回写部分 content / status=INTERRUPTED 或 FAILED
        S-->>F: 连接断开或 message_error
    end
```

## 3. 中断后继续生成时序图

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端 ChatView/chatStore
    participant C as ChatMessageController
    participant S as ChatMessageServiceImpl
    participant DB as MySQL
    participant M as DeepSeek

    U->>F: 点击继续生成
    F->>C: POST /api/chat/message/regenerate
    C->>S: regenerateMessage(userId, request, emitter)
    S->>DB: 查询目标 assistant message
    S->>DB: 校验 status=INTERRUPTED
    S->>DB: 查询上一条 user message
    S->>S: 构造 continue prompt + assistantPrefix
    S->>DB: 原子更新 status: INTERRUPTED -> GENERATING
    S-->>F: SSE message_start(同一 messageId)
    S->>M: streamChat(resumeModelRequest)
    loop 继续生成 delta
        M-->>S: delta chunk
        S->>DB: 继续按阈值回写 content
        S-->>F: SSE message_delta
    end
    alt 正常结束
        M-->>S: finishReason / usage
        S->>DB: 同一 messageId 回写完整内容 / status=NORMAL
        S-->>F: SSE message_end
    else 再次中断
        S->>DB: 保留已追加内容 / status=INTERRUPTED
    end
```

## 4. 消息状态流转图

```mermaid
stateDiagram-v2
    [*] --> GENERATING: 创建 assistant 占位消息
    GENERATING --> NORMAL: 正常流式结束
    GENERATING --> INTERRUPTED: 已有部分内容且连接中断
    GENERATING --> FAILED: 首包前失败或不可恢复错误
    INTERRUPTED --> GENERATING: 用户点击继续生成
    INTERRUPTED --> NORMAL: 继续生成成功结束
    INTERRUPTED --> FAILED: 恢复过程中发生不可恢复错误
    NORMAL --> [*]
    FAILED --> [*]
```

## 5. 前端处理分支图

```mermaid
flowchart TD
    A["前端收到 SSE 事件"] --> B{"事件类型"}
    B -- "message_start" --> C["插入或复用 assistant 消息"]
    B -- "message_reasoning_delta" --> D["向同一 messageId 追加 reasoningContent"]
    B -- "message_delta" --> E["向同一 messageId 追加 content"]
    B -- "message_end" --> F["状态改为 done"]
    B -- "message_error" --> G["状态改为 error"]
    A --> K{"fetch / SSE 是否被 abort 或中断?"}
    K -- "是且已有部分内容" --> H["本地先标记 interrupted"]
    H --> I["稍后重新 loadMessages 回查数据库真实状态"]
    K -- "是但没有部分内容" --> J["显示发送失败"]
```

## 6. 后端关键决策点

```mermaid
flowchart TD
    A["进入 regenerateMessage"] --> B["查询目标 message"]
    B --> C{"role == assistant?"}
    C -- "否" --> X["抛 CHAT_MESSAGE_NOT_INTERRUPTIBLE"]
    C -- "是" --> D{"status == GENERATING?"}
    D -- "是" --> Y["抛 CHAT_MESSAGE_ALREADY_GENERATING"]
    D -- "否" --> E{"status == INTERRUPTED?"}
    E -- "否" --> Z["抛 CHAT_MESSAGE_NOT_INTERRUPTIBLE"]
    E -- "是" --> F["查询上一条 user message"]
    F --> G["构造 continue prompt"]
    G --> H["原子更新 status: INTERRUPTED -> GENERATING"]
    H --> I{"更新成功?"}
    I -- "否" --> Y
    I -- "是" --> J["继续流式生成"]
```

## 7. 和代码的对应关系

| 流程节点 | 代码位置 |
| --- | --- |
| 首次发送消息入口 | [ChatMessageController.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/controller/ChatMessageController.java:57) |
| 继续生成入口 | [ChatMessageController.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/controller/ChatMessageController.java:74) |
| 首次发送和异步流式执行 | [ChatMessageServiceImpl.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/service/impl/ChatMessageServiceImpl.java:123) |
| 中断后继续生成主逻辑 | [ChatMessageServiceImpl.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/service/impl/ChatMessageServiceImpl.java:171) |
| 部分内容回写和中断状态落库 | [ChatMessageServiceImpl.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/service/impl/ChatMessageServiceImpl.java:516) |
| 前端流式消费和 abort | [chat.ts](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/src/stores/chat.ts:365) |
| 前端继续生成按钮和停止生成按钮 | [ChatView.vue](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/src/views/ChatView.vue:204) |

## 8. 阅读建议

- 如果你要看整体设计，先读本文件，再读 [chat-response-resume-design.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/chat-response-resume-design.md:1)。
- 如果你要开始联调，优先看 [api-test-cases.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/api-test-cases.md:308)。
- 如果你要改实现细节，重点对照 [ChatMessageServiceImpl.java](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/java/com/example/aichat/modules/chat/service/impl/ChatMessageServiceImpl.java:171) 和 [chat.ts](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/src/stores/chat.ts:365)。
