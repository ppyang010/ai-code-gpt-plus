# 聊天响应中断后继续技术方案

最后更新：2026-05-20

## 1. 文档目标

本文档用于明确 GPT Plus 项目第一版“聊天响应中断后继续”的技术方案，覆盖问题边界、现有代码基础、后端接口、消息状态、前端交互和实现顺序，作为后续开发的统一依据。

配套流程图文档见：

- [chat-response-resume-flow.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/chat-response-resume-flow.md)

当前方案面向 MVP，优先保证：

- 流式回答在网络中断、页面刷新或前端主动停止后，可以继续生成
- 中断前已经生成的内容可以保留，而不是整段回答丢失
- 前端能明确区分“重新生成”和“继续生成”
- 方案尽量复用现有 `send + regenerate + SSE` 链路，降低改造成本

当前阶段暂不追求：

- 提供商级别的真实流恢复
- 精确从 token 断点继续
- 多端同时接力同一条流式响应
- 复杂的流式审计或回放中心

## 2. 适用范围

这里的“中断后继续”指的是聊天回答链路中断后，应用层继续生成剩余回答，而不是图片上传断点续传。

第一版覆盖以下中断场景：

- 浏览器刷新导致 SSE 连接断开
- 前端页面切换或组件卸载导致流式消费终止
- 网络抖动导致 SSE 读取失败
- 用户手动点击“停止生成”后，稍后点击“继续生成”

第一版不覆盖：

- 上传文件的断点续传
- 模型提供商侧原生恢复同一请求
- 已结束正常回答的自动续写
- 多轮上下文被改写后的历史回答修复

## 3. 当前代码基础

```text
前端 sendMessage
  -> POST /api/chat/message/send
  -> 后端创建 assistant 占位消息
  -> SSE 推送 message_start / message_reasoning_delta(可选) / message_delta / message_end
  -> 当前前端仅在内存拼接 delta
  -> 当前后端仅在成功结束时一次性回写完整内容
```

已确认的现有代码基础：

| 位置 | 现状 |
| --- | --- |
| `ChatMessageController` | 已提供 `POST /api/chat/message/send` 和 `POST /api/chat/message/regenerate` 两个 SSE 接口入口。 |
| `ChatMessageServiceImpl.sendMessage` | 已能创建用户消息、assistant 占位消息并启动异步流式回答。 |
| `ChatMessageServiceImpl.regenerateMessage` | 已有方法签名，但当前直接抛出“未实现”。 |
| `MessageStatusEnum` | 已预留 `INTERRUPTED(2)` 状态，可以直接复用。 |
| `gpt-plus-web/src/stores/chat.ts` | 当前只把 delta 拼在前端内存里，中断后不会自动恢复。 |

## 4. 问题定义

```text
用户发送消息
  -> assistant 占位消息已建
  -> SSE 流式返回到一半中断
  -> 当前前端丢失后续连接
  -> 当前后端通常只在结束时回写完整 content
  -> 用户刷新后只能看到失败/空消息，不能继续
```

核心问题有两个：

- 如何把“这条回答中断了，但已经生成了一部分”稳定落库
- 如何在不中断会话上下文的前提下继续生成剩余回答

## 5. 方案边界

第一版明确采用“应用层继续生成”，而不是“模型提供商原生恢复同一流”。

这意味着：

- 后端不会要求模型从上一次 HTTP 流的底层连接继续吐 token
- 后端会基于“已生成前缀 + 原始用户问题 + 历史上下文”再发起一次新的模型请求
- 前端对用户暴露的文案是“继续生成”，但技术实现本质上是一种受控续写

## 6. 核心设计

### 6.1 目标效果

- 回答流中断后，消息状态变成 `INTERRUPTED`
- 已生成的前半段内容仍保留在 `chat_message.content`
- 消息气泡展示“继续生成”按钮
- 用户点击后，从当前中断内容往后继续，而不是整段重来
- 继续完成后，仍归属同一条 assistant 消息

### 6.2 为什么继续写回同一条消息

- 和当前消息列表结构最一致，不需要引入“半条旧回答 + 一条新回答”的双气泡
- 前端渲染和滚动逻辑更简单
- 用户视角更自然，这仍然是同一条回答
- `MessageStatusEnum.INTERRUPTED` 已能表达该语义

### 6.3 核心对象

| 对象 | 作用 |
| --- | --- |
| `regenerateMessageId` | 继续生成时指向那条中断的 assistant 消息 |
| `MessageStatusEnum.INTERRUPTED` | 标记这条消息曾经生成到一半被中断 |
| `assistantPrefix` | 已生成并持久化的回答前缀 |
| `resumePrompt` | 给模型的继续生成指令，要求从前缀后继续且避免重复 |

## 7. 后端接口方案

第一版建议复用现有 `POST /api/chat/message/regenerate`，不额外新增新路径。

### 7.1 为什么复用 `regenerate`

- 当前控制器和 DTO 已经存在
- 前后端改动范围更小
- UI 上“重新生成”和“继续生成”只是同一入口的两种文案和分支

### 7.2 接口

```http
POST /api/chat/message/regenerate
Content-Type: application/json
Accept: text/event-stream
```

请求 DTO 沿用现有结构：

```json
{
  "sessionId": 1001,
  "regenerateMessageId": 9003,
  "modelId": 2001,
  "modeCode": "expert"
}
```

### 7.3 语义约定

- 如果目标消息状态是 `INTERRUPTED`，按“继续生成”处理
- 如果目标消息状态是 `FAILED` 或用户主动选择重新生成，按“重新生成”处理
- 第一版可以只先支持 `INTERRUPTED` 分支，正常重新生成继续保留未实现或后置

## 8. 消息状态设计

沿用现有枚举，语义明确化即可：

| 状态 | 含义 |
| --- | --- |
| `GENERATING` | 正在流式生成 |
| `INTERRUPTED` | 生成中断，但已保留部分内容，可继续生成 |
| `NORMAL` | 正常完成 |
| `FAILED` | 本次生成失败且不可直接继续 |

建议状态流转：

```text
GENERATING -> NORMAL
GENERATING -> INTERRUPTED
GENERATING -> FAILED
INTERRUPTED -> GENERATING -> NORMAL
```

## 9. 后端处理流程

### 9.1 首次发送消息

```text
sendMessage
  -> 创建 user message
  -> 创建 assistant 占位 message(status=GENERATING)
  -> SSE 推流
  -> 周期性回写部分 content
  -> 正常结束时 status=NORMAL
  -> 中断时 status=INTERRUPTED 或 FAILED
```

### 9.2 中断后继续生成

```text
regenerateMessage
  -> 读取目标 assistant message
  -> 校验状态必须是 INTERRUPTED
  -> 读取对应 user message 与历史上下文
  -> 取出 assistantPrefix
  -> 构造 continue prompt
  -> 将同一 message status 改回 GENERATING
  -> 继续流式生成并追加 content
  -> 结束后 status=NORMAL
```

### 9.3 继续生成时的上下文构造

建议不要把“已中断的 assistant 半成品”直接作为普通历史消息再次传给模型，否则容易让模型重复自己刚写过的话。

建议做法：

1. 加载该 assistant 消息之前的历史上下文
2. 找到与之对应的上一条 user 消息
3. 取出当前 assistant 已生成内容作为 `assistantPrefix`
4. 给模型补一段继续生成指令，例如：

```text
下面是一段被中断的回答，请从它停止的位置继续往后写。
不要重复已经输出的内容，不要重新开头，不要改写前文语气。
已生成前缀：
{assistantPrefix}
```

### 9.4 为什么不直接从 partial assistant 继续拼上下文

- 模型对“半条 assistant 历史”的理解不稳定，容易接着分析前文而不是继续写
- 用显式 continue prompt 更容易控制“不重复、不重开头”
- 这是应用层续写，更符合当前 MVP 的稳定性目标

## 10. 部分内容持久化策略

这是当前实现里最关键的补点。

当前 `ChatMessageServiceImpl` 只在流式成功结束后一次性 `updateById`，这会导致中断时丢掉已生成片段。

建议改为“增量缓存 + 周期性落库”：

```text
chunk 到达
  -> 先追加到内存 StringBuilder
  -> 每累计 N 个字符或每隔 T 秒 update chat_message.content
  -> 如果推流结束，最终再做一次完整回写
  -> 如果推流异常，保留最近一次已落库内容并标记 INTERRUPTED
```

建议第一版参数：

- `N = 80 ~ 200` 个字符
- `T = 1 ~ 2` 秒

这样能在数据库写入次数和恢复可见性之间取平衡。

## 11. 中断判定与异常分流

### 11.1 判定为 `INTERRUPTED`

- SSE 连接断开
- 前端主动停止生成
- 推流过程中 `emitter.send` 失败，但已有部分内容成功生成

### 11.2 判定为 `FAILED`

- 模型调用在首个有效 delta 前就报错
- 请求参数、会话、模型校验失败
- 无法定位对应 user message 或消息归属不合法

## 12. 前端交互方案

### 12.1 页面行为

- assistant 消息状态为 `INTERRUPTED` 时，展示“继续生成”按钮
- assistant 消息状态为 `FAILED` 时，展示“重试”或“重新生成”按钮
- 若用户刷新页面，重新加载消息列表后仍能看到中断消息和继续入口

### 12.2 前端 store 改造点

当前 `gpt-plus-web/src/stores/chat.ts` 需要重点调整：

1. `message_start` 时如果目标消息已存在，不再重复插入，而是切回 `streaming`
2. `message_delta` 时继续向同一消息追加文本
3. 本地 abort 或 SSE 失败时，把对应消息标记为 `interrupted`，而不是一律走 `error`
4. 新增 `continueMessage(messageId)`，调用 `/chat/message/regenerate`

### 12.3 前端文案区分

| 场景 | 按钮文案 |
| --- | --- |
| 回答被中断 | `继续生成` |
| 回答失败且无前缀可复用 | `重新生成` |
| 用户再次发送同样问题 | `重试发送` |

## 13. 数据库存量与表结构影响

第一版建议不新增表，优先复用现有 `chat_message`：

- `content`：存部分内容和最终内容
- `status`：区分 `GENERATING / INTERRUPTED / NORMAL / FAILED`
- `finish_reason`：可补 `interrupted` / `error` / `stop`
- `updated_at`：天然可反映最近一次续写时间

如果后续需要更精细的流式审计，再考虑新增流事件表。

## 14. 幂等与并发控制

### 14.1 幂等要求

- 同一条 `INTERRUPTED` 消息，如果已经有一次继续生成在跑，不能重复触发第二次
- 同一用户不能同时对同一 `messageId` 发起多个 continue 请求

### 14.2 建议控制方式

- 继续生成前先校验状态是否仍是 `INTERRUPTED`
- 将状态原子更新为 `GENERATING` 成功后，才允许真正调用模型
- 若更新失败，直接返回“该消息已在继续生成中”

## 15. API 与事件建议

SSE 事件类型可以继续沿用当前协议：

- `message_start`
- `message_delta`
- `message_end`
- `message_error`

第一版不强制新增 `message_interrupted` 事件，因为网络断开时本来也无法稳定送达；中断状态更适合通过消息列表回查和继续接口体现。

可选增强字段：

- `message_start.resume = true`
- `message_start.resumeFromLength = 420`

这样前端可以在继续生成时展示“从上次中断处继续”。

## 16. 实现顺序建议

建议按下面顺序落地，保证每一步都能独立验证：

1. 实现 `regenerateMessage` 的 `INTERRUPTED` 分支
2. 补流式部分内容的周期性落库
3. 中断时把消息状态改成 `INTERRUPTED`
4. 前端消息卡片增加“继续生成”入口
5. 前端接入 `continueMessage(messageId)` 和中断态展示
6. 补真实 API 测试用例和联调结果文档

## 17. 本轮明确不做的内容

- 提供商原生续流
- token 级精确恢复
- 自动去重所有模型重复前缀
- 多端同步接力
- 完整流事件审计中心

## 18. 对当前仓库的直接影响

本方案落地后，建议同步补以下内容：

- `docs/backend/chat-api-dto-design.md`：把“图片断点续传”修正为“聊天响应中断后继续”
- `docs/backend/api-test-cases.md`：补中断后继续生成的接口测试用例
- `docs/frontend/task.md`：把“断点续传交互”修正为“继续生成交互”
- `docs/backend/task.md`：把“断点续传能力”修正为“聊天响应中断后继续能力”
