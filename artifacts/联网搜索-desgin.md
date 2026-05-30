# 联网搜索 设计文档

最后更新：2026-05-28

## 文档说明

- 本文档在需求讨论收敛后生成，用于记录目标、代码落点、设计边界和验收口径。
- 本文档负责沉淀需求定义、设计边界、代码落点和实施计划，是 `联网搜索-task.md` 的设计依据。
- 配套任务跟踪文档：`联网搜索-task.md`
- 若需求目标、边界、接口、字段、数据结构、实现顺序、验收口径、任务优先级或范围出现变动，先更新本文档，再同步 `联网搜索-task.md`。

## 阶段 A 结论沉淀

## 代码落点判断

- 统一入口：
  - 后端发送链路：`/api/chat/message/send` -> `ChatMessageController.sendMessage(...)` -> `ChatMessageServiceImpl.sendMessage(...)`
  - 后端继续生成链路：`/api/chat/message/regenerate` -> `ChatMessageController.regenerateMessage(...)` -> `ChatMessageServiceImpl.regenerateMessage(...)`
  - 前端发送链路：`ChatView.vue` 输入区 -> `handleSendClick(...)` -> `useChatStore.sendMessage(...)`
- 涉及模块：
  - 后端：`modules/chat`、`common`、新增 `infrastructure/search`
  - 前端：`gpt-plus-web/src/types/chat.ts`、`gpt-plus-web/src/stores/chat.ts`、`gpt-plus-web/src/views/ChatView.vue`
- 现有可复用能力：
  - `ChatMessageSendRequest` 已预留 `enableWebSearch`
  - `ChatPromptResolver` 已负责拼接模式提示词、会话提示词和本次请求提示词
  - `chat_message.metadata` 已是 JSON 扩展位，当前仅用于附件
  - `ChatMessageServiceImpl` 已完整收口消息发送、SSE、继续生成和消息列表回显
  - `ChatModelClient` / `ChatModelClientRegistry` 已提供外部能力适配分层参考
- 不建议落点：
  - 不建议把具体搜索供应商调用直接写进 `ChatMessageServiceImpl`
  - 不建议第一版为联网搜索单独新建数据库表
  - 不建议仅在前端本地状态保存“是否联网搜索”，否则刷新消息列表后无法回显
- 推荐实现位置：
  - 在 `modules/chat/service/` 下新增 `WebSearchService` 领域抽象，由聊天业务依赖该抽象
  - 在 `modules/chat/service/impl/` 下新增 `DefaultWebSearchService`
  - 在 `infrastructure/search/` 下新增具体供应商 client 与配置
  - 在 `chat_message.metadata` 中持久化搜索上下文，确保刷新和继续生成都能复用

## 需求术语映射

| 需求术语 | 代码对象 | 差异说明 |
| --- | --- | --- |
| 联网搜索开关 | 后端 `ChatMessageSendRequest.enableWebSearch` + `webSearchMode`；前端 `ChatMessageSendRequest.enableWebSearch` + `webSearchMode` 和 store 三态状态 | 第一版由后端根据 `webSearchMode + content` 统一解析最终是否联网，前端只负责选择模式 |
| 联网搜索结果摘要 | 进入模型前拼到 `finalPrompt` 的一段系统提示词补充 | 不是单独消息内容字段，也不直接拼进用户输入 |
| 搜索来源 / 引用 | `chat_message.metadata` 中的 JSON 扩展字段 | 当前仓库没有专门的搜索结果表，也没有前端来源展示契约 |
| 继续生成 | `ChatMessageServiceImpl.regenerateMessage(...)` | 当前继续生成链路不会重新收请求体里的 `enableWebSearch`，必须复用持久化的搜索上下文 |
| 结果提示交互 | `ChatMessageItemVO` / 前端 message type 中新增显式标志位，例如 `webSearchEnabled` | 第一版不做独立搜索结果页，只做已联网搜索的提示回显 |

## 风险与边界结论

- 当前最大隐含风险不是“前端没开关”，而是“继续生成会丢失联网搜索上下文”。
  - 现状里 `regenerateMessage(...)` 只会重建模式提示词和历史消息，不会重新执行搜索。
  - 如果搜索摘要不持久化，联网搜索回答一旦中断，继续生成会退化成普通续写。
- 第一版不新增 DDL / DML。
  - `chat_message.metadata` 已经是 JSON 扩展位，足够承载 `webSearchEnabled`、搜索摘要和来源列表。
- 第一版不把来源列表直接做成聊天区独立结果卡片。
  - 现有前端待办是“开关与结果提示交互”，不是“完整引用来源展示页”。
  - 但后端 metadata 仍应保留结构化结果，为下一阶段来源展示留口。
- 联网搜索的“自动判断”第一版不做复杂分类器。
  - 先用可扩展的意图规则表实现，按优先级定义“哪些意图要联网、哪些不要联网”。
  - 规则表统一放在后端判定，保证多端入口和直调接口时行为一致。
- 联网搜索是显式用户选择或显式命中的自动规则，搜索失败不应静默降级成普通聊天。
  - 否则用户会误以为回答已经联网。
  - 推荐第一版采用“失败显式报错，成功才进入模型调用”的策略。
- 搜索成功但无可靠结果，不等同于技术失败。
  - 这种情况可以继续调用模型，但需要把“未检索到可靠外部结果，请勿伪造引用”明确注入提示词。
- 搜索摘要必须做长度控制。
  - 第一版建议只取前 3 到 5 条结果
  - 每条仅保留标题、摘要、来源 URL、抓取时间
  - 注入 prompt 前统一裁剪，避免显著推高 token

## 当前未确认点 / 暂不处理范围

- 当前仓库里还没有任何搜索供应商配置项，真实搜索供应商与密钥来源未确认。
- 第一版暂不处理：
  - 独立搜索结果页
  - 多供应商搜索路由
  - 搜索结果缓存
  - 搜索来源的完整前端卡片展示

## 方案设计与实施计划

### 1. 背景

当前项目已经把 `enableWebSearch` 设计进后端请求 DTO 和接口文档，但链路仍停留在“字段预留”状态：

- 后端 `ChatMessageSendRequest` 已有 `enableWebSearch`
- `ChatMessageServiceImpl.sendMessage(...)` 没有读取这个字段
- 前端 `ChatMessageSendRequest` 类型和 `useChatStore.sendMessage(...)` 仍未携带该字段
- 页面层 `ChatView.vue` 也没有开关入口

因此，“联网搜索实现”的核心不是新增一个按钮，而是把“搜索 -> 摘要注入 -> 持久化 -> 刷新回显 -> 中断后继续生成”这条链路真正闭环。

### 2. 功能清单

- 在前端输入区增加“联网搜索”开关
- 发送消息时把 `enableWebSearch` 透传到后端
- 后端在 `enableWebSearch=true` 时先执行搜索，再把摘要注入模型上下文
- 后端把搜索上下文持久化到消息 metadata，支持刷新回显和继续生成复用
- 消息列表返回显式 `webSearchEnabled` 标志位，前端对已联网搜索的 assistant 回复做提示
- 搜索失败时显式报错，不静默降级

### 3. 各模块详细设计

#### 3.1 后端：搜索抽象

建议新增以下对象：

- `modules/chat/service/WebSearchService`
  - 负责按聊天场景触发搜索，并返回统一的结构化结果
- `modules/chat/service/impl/DefaultWebSearchService`
  - 负责业务编排、结果裁剪、摘要构造
- `infrastructure/search/WebSearchClient`
  - 负责具体供应商调用
- `infrastructure/search/...Properties`
  - 负责配置供应商地址、API Key、超时时间、结果条数上限

建议的数据结构：

- `WebSearchResultItem`
  - `title`
  - `snippet`
  - `sourceUrl`
  - `fetchedAt`
- `WebSearchContext`
  - `enabled`
  - `summaryPrompt`
  - `results`

#### 3.2 后端：发送消息链路

`ChatMessageServiceImpl.sendMessage(...)` 建议按下面顺序调整：

1. 校验会话、模型、附件
2. 如果 `enableWebSearch=true`
   - 调用 `WebSearchService.search(...)`
   - 得到结构化搜索结果和 prompt 摘要
3. 使用 `ChatPromptResolver` 先拼模式 / 会话 / 请求提示词
4. 再把 `summaryPrompt` 追加到最终 `finalPrompt`
5. 创建 assistant 占位消息时，把搜索上下文写入 `metadata`
6. 后续流式调用、usage 落库、SSE 推送保持不变

assistant message 的 metadata 建议结构：

```json
{
  "webSearchEnabled": true,
  "webSearchSummary": "...",
  "webSearchResults": [
    {
      "title": "...",
      "snippet": "...",
      "sourceUrl": "...",
      "fetchedAt": "2026-05-28T10:00:00"
    }
  ]
}
```

第一版不要求把这份结构完整透给前端，但必须在后端持久化。

#### 3.3 后端：继续生成链路

`regenerateMessage(...)` 不新增 `enableWebSearch` 入参，继续沿用当前契约。

推荐方案：

- 从被继续生成的 assistant message 的 `metadata` 读取 `webSearchSummary`
- 如果存在，则在 `basePrompt` 上重新追加这段摘要
- 再进入 `buildResumeModelRequest(...)`

这样可以避免：

- 继续生成时重复搜索
- 同一条回答在中断后丢失联网搜索上下文
- 前后两次回答由于搜索实时变化导致上下文不一致

#### 3.4 后端：消息列表回显

当前 `ChatMessageItemVO` 只显式返回附件，不返回搜索状态。

第一版建议：

- `ChatMessageItemVO` 新增 `Boolean webSearchEnabled`
- `toMessageItem(...)` 从 `metadata` 里解析该字段
- 前端消息列表只根据该字段决定是否展示“已联网搜索”提示

第一版不要求在 `ChatMessageItemVO` 中直接返回完整 `webSearchResults`。

#### 3.5 前端：输入与消息提示

前端改造点：

- `gpt-plus-web/src/types/chat.ts`
  - `ChatMessageSendRequest` 增加 `enableWebSearch?: boolean`
  - `ChatMessageSendRequest` 增加 `webSearchMode?: 'disabled' | 'enabled' | 'auto'`
- `gpt-plus-web/src/stores/chat.ts`
  - 增加 store 级三态状态，例如 `currentWebSearchMode`
  - 发送 payload 时携带 `webSearchMode`
  - 手动“开 / 关”可兼容携带 `enableWebSearch` 兜底，但不再按内容做本地判断
- `gpt-plus-core/src/main/java/com/example/aichat/modules/chat/service/WebSearchIntentResolver.java`
  - 维护自动判断的意图规则表和解析入口
  - 每条规则定义 `id`、`label`、`action(enable/disable)`、`priority` 和 `patterns`
- `gpt-plus-web/src/views/ChatView.vue`
  - 在 composer 现有 upload / mode 区域增加联网搜索三态选择
  - 对 `auto` 模式显示“服务端会按意图规则判断”的轻提示

推荐 UI 落点：

- 开关位置：`composer__footer-prefix` 或 `composer__suffix-actions`，与“添加图片”和“当前模式”同层
- 自动判断提示位置：composer footer 内的轻量 caption，不做新面板

### 4. 业务流程

#### 4.1 发送消息

1. 用户选择“关闭 / 开启 / 自动判断”之一并发送消息
2. 前端把 `webSearchMode` 传到 `/api/chat/message/send`
3. 后端根据 `webSearchMode + content` 统一判断最终是否联网
4. 后端校验会话 / 模型 / 附件
5. 当 `enableWebSearch=true` 时，后端执行联网搜索
6. 后端把搜索摘要追加到最终 prompt
7. 后端创建带搜索 metadata 的 assistant 占位消息
8. 后端调用模型并通过 SSE 推流
9. 流结束后回写完整消息、token 和 metadata

#### 4.2 继续生成

1. 用户对一条已中断的 assistant 消息点击“继续生成”
2. 后端读取该 assistant 消息的 `metadata`
3. 若存在 `webSearchSummary`，则把它重新注入 `basePrompt`
4. 按现有 `regenerate` 逻辑继续流式输出

### 5. 接口 / 数据影响

#### 5.1 数据库

- 不新增表
- 不改 schema
- 复用 `chat_message.metadata JSON`

#### 5.2 后端接口契约

- `POST /api/chat/message/send`
  - 沿用已有 `enableWebSearch`
  - 新增 `webSearchMode`
  - 无需新增 controller 接口
- `GET /api/chat/message/list`
  - `ChatMessageItemVO` 新增 `webSearchEnabled`
- `POST /api/chat/message/regenerate`
  - 第一版不改入参

#### 5.3 前端类型契约

- `ChatMessageSendRequest.enableWebSearch?: boolean`
- `ChatMessageSendRequest.webSearchMode?: 'disabled' | 'enabled' | 'auto'`
- store 内新增 `currentWebSearchMode`

#### 5.4 配置影响

- `application.yml` / `application-local.example.yml` / `application-local.yml`
  - 新增搜索供应商配置段
- `README` 或 `backend-local-setup.md`
  - 后续需要补本地配置说明

### 6. 边界与失败处理

- `enableWebSearch=false`
- `webSearchMode=auto` 但未命中任何联网意图
- `webSearchMode=auto` 命中“写作/总结/解释”等非联网意图
  - 保持现有行为，不触发任何搜索逻辑
- `enableWebSearch=true` 但搜索请求超时 / 401 / 5xx
  - 本次发送失败，返回明确错误，不进入模型调用
- 搜索请求成功但结果为空
  - 允许继续调用模型
  - 注入“未检索到可靠外部结果，请勿伪造来源”的提示词
- 继续生成时 message metadata 不含搜索摘要
  - 按普通继续生成处理
  - 兼容历史老消息
- 历史消息刷新
  - 只要 `metadata` 里有 `webSearchEnabled`，消息列表就应保持回显一致

### 7. 验收清单

- [ ] 前端可以显式打开 / 关闭联网搜索开关
- [ ] 打开后，发送消息 payload 会带 `enableWebSearch=true`
- [ ] 后端会在模型调用前先执行搜索，再注入 prompt
- [ ] assistant 消息 metadata 会持久化搜索上下文
- [ ] 消息列表刷新后，已联网搜索的 assistant 回复仍能显示结果提示
- [ ] 联网搜索回答中断后，继续生成不会丢失搜索上下文
- [ ] 搜索失败时会显式报错，不会静默降级成普通聊天

### 8. 非功能需求

- 搜索请求应设置独立超时，避免无限阻塞聊天请求
- 搜索摘要应限制条数和长度，避免显著抬高 token 成本
- 日志中不记录完整搜索结果、完整 prompt 或敏感凭证
- 对老消息兼容：`metadata` 缺失搜索字段时，不影响消息列表展示
- 第一版不做缓存，以链路闭环和可解释性优先

### 9. 测试与验收用例

- `enableWebSearch=false` 的普通发送回归
- `enableWebSearch=true` 且搜索成功的发送链路
- 搜索成功但空结果的发送链路
- 搜索超时 / 供应商失败时的失败分支
- 联网搜索回答中断后 `regenerate` 的上下文恢复
- 消息列表刷新 / 页面刷新后的 `webSearchEnabled` 回显
- 前端开关状态切换、payload 透传和提示文案展示

## 变更记录

| 日期 | 变更内容 | 影响文档 |
| --- | --- | --- |
| 2026-05-28 | 初始化联网搜索设计文档 | `联网搜索-task.md` |
