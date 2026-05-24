# 聊天模块接口文档 + DTO/VO 设计

## 设计目标

这份文档用于定义 AI 聊天网站第一版的聊天模块接口，覆盖：

- 会话管理
- 历史消息查询
- 发送消息
- SSE 流式返回
- 基础 DTO / VO 设计

设计目标是让前端 `Vue` 和后端 `Spring Boot` 可以并行开发，并为后续扩展多模型、额度、联网搜索预留空间，同时把图片上传和聊天响应中断后继续纳入本轮需求设计范围。

## 范围说明

当前版本包含：

- 多会话聊天
- 多模型切换
- 快速模式 / 专家模式
- 模式绑定不同提示词模板
- 消息持久化
- SSE 流式输出
- token 和调用日志记录
- 图片上传
- 聊天响应中断后继续

当前版本不做：

- 额度拦截
- 充值扣费
- 联网搜索
- 多端同步冲突处理

补充说明：

- 图片上传当前已收敛为非断点续传直传版本，并已接入 `attachmentIds` 关联聊天消息
- 聊天响应中断后继续当前已进入代码实现并完成本地联调验证
- 联网搜索当前不再只作为预留字段，后续需要按明确能力实现 `enableWebSearch`

## 模式设计

第一版中，`快速模式 / 专家模式` 不单独实现成两套聊天逻辑，而是实现为：

- 一个 `modeCode`
- 绑定一份默认提示词模板
- 可选绑定默认模型
- 可选绑定默认参数

也就是说，模式本质上是“回答策略预设”。

### 推荐模式定义

#### quick

- 目标：更快返回结果
- 风格：直接给答案，少铺垫
- 默认提示词方向：简洁、结论优先、步骤适中
- 适合：日常问答、代码片段、简短说明

#### expert

- 目标：更完整、更结构化
- 风格：先分析，再分步骤输出，补充风险和注意事项
- 默认提示词方向：强调推理过程、方案对比、结构化输出
- 适合：架构设计、复杂排查、方案评估、长文生成

### 模式实现原则

后端收到 `modeCode` 后，优先根据模式加载对应提示词模板，再组合：

1. 模式默认提示词
2. 会话级 `systemPrompt`
3. 本次请求级 `systemPrompt`

最终拼装成发送给模型的系统提示词。

### 建议的优先级

```text
请求级 systemPrompt > 会话级 systemPrompt > 模式默认提示词
```

更准确地说，建议是“追加合并”而不是完全覆盖：

```text
最终 system prompt =
模式默认提示词
+ 会话级附加提示词
+ 本次请求附加提示词
```

这样能保留模式差异，同时允许用户对单个会话或单次请求做补充控制。

### 推荐的模式提示词示例

#### quick 模式提示词示例

```text
你是一个高效、直接的 AI 助手。
请优先给出结论，再补充必要说明。
回答尽量简洁清晰，避免冗长铺垫。
如果问题包含实现需求，请优先给出可执行方案。
```

#### expert 模式提示词示例

```text
你是一个专业顾问型 AI 助手。
请先理解问题背景，再给出结构化分析。
输出时尽量包含：结论、原因、步骤、风险、建议。
如果存在多种方案，请做简要对比并说明取舍。
```

## 统一约定

## 基础路径

```text
/api/chat
```

## 鉴权方式

默认使用：

```text
Authorization: Bearer <token>
```

## 数据格式

- 普通接口：`application/json`
- 流式接口：`text/event-stream`

## 时间格式

统一使用：

```text
yyyy-MM-dd HH:mm:ss
```

也可以在前后端约定后统一改成 ISO 8601。

## 通用响应结构

建议普通 JSON 接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

推荐约定：

- `code = 0` 表示成功
- 非 `0` 表示失败

## 通用分页结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "pageNo": 1,
    "pageSize": 20,
    "total": 100
  }
}
```

## 模块接口总览

第一版建议提供以下接口：

1. `GET /api/chat/session/list` 获取会话列表
2. `POST /api/chat/session/create` 创建会话
3. `POST /api/chat/session/update-title` 修改会话标题
4. `POST /api/chat/session/delete` 删除会话
5. `GET /api/chat/message/list` 获取会话消息列表
6. `POST /api/chat/message/send` 发送消息并流式返回
7. `POST /api/chat/message/regenerate` 重新生成回答
8. `GET /api/model/list` 获取启用模型列表
9. `GET /api/health` 健康检查
10. `POST /api/file/upload/image` 直传图片并生成附件
11. `GET /api/file/content/{fileId}` 读取图片内容

如果你想让“发消息”也走普通接口，后面可以再加：

- `POST /api/chat/message/send-sync`

但第一版更建议主用 SSE。

---

## 模型列表接口

### 接口

```http
GET /api/model/list
```

### 响应 DTO

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 2001,
      "code": "deepseek-v4-flash",
      "label": "DeepSeek V4 Flash",
      "modelType": "chat",
      "supportStream": true,
      "supportThinking": false,
      "supportJsonOutput": true,
      "supportVision": false,
      "supportFile": false,
      "contextWindow": 64000,
      "maxOutputTokens": 8000
    }
  ]
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 模型配置 ID，聊天请求中的 `modelId` 使用该值 |
| code | String | 模型编码，如 `deepseek-v4-flash` |
| label | String | 前端展示名 |
| modelType | String | 模型类型，如 `chat` |
| supportStream | Boolean | 是否支持流式输出 |
| supportThinking | Boolean | 是否支持思考模式 |
| supportJsonOutput | Boolean | 是否支持 JSON Output |
| supportVision | Boolean | 是否支持图片 |
| supportFile | Boolean | 是否支持文件 |
| contextWindow | Integer | 上下文窗口 |
| maxOutputTokens | Integer | 最大输出 token 数 |

---

## 1. 获取会话列表

### 接口

```http
GET /api/chat/session/list?pageNo=1&pageSize=20
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| pageNo | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页大小，默认 20 |

### 页面交互约束

- 左侧 session 列表需要按分页结果分批加载，避免会话增多后继续拉伸页面高度
- 前端聊天页需要保持单屏布局：左侧 session 区、右侧消息区和底部输入区都固定在一个视口内
- 当 session 超过当前页容量时，前端通过“继续加载”或滚动触底加载下一页，而不是让输入框被挤出视口

### 响应 DTO

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "sessionId": 1001,
        "title": "帮我写一份 Vue3 聊天页",
        "modeCode": "quick",
        "defaultModelId": 2001,
        "defaultModelCode": "deepseek-v4-flash",
        "defaultModelName": "DeepSeek V4 Flash",
        "lastMessagePreview": "可以，我先帮你拆分页面结构...",
        "lastMessageAt": "2026-04-21 15:00:00",
        "createdAt": "2026-04-21 14:30:00"
      }
    ],
    "pageNo": 1,
    "pageSize": 20,
    "total": 1
  }
}
```

### 对应 VO

- `ChatSessionListItemVO`
- `PageResponse<ChatSessionListItemVO>`

---

## 2. 创建会话

### 接口

```http
POST /api/chat/session/create
Content-Type: application/json
```

### 请求 DTO

```json
{
  "title": "新对话",
  "modeCode": "quick",
  "defaultModelId": 2001,
  "systemPrompt": "回答时尽量结合 Spring Boot 场景"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| title | String | 否 | 会话标题，可为空 |
| modeCode | String | 是 | `quick` / `expert`，会影响默认提示词模板 |
| defaultModelId | Long | 否 | 默认模型 ID |
| systemPrompt | String | 否 | 会话级附加提示词，不替代模式默认提示词 |

### 响应 VO

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": 1001,
    "title": "新对话",
    "modeCode": "quick",
    "defaultModelId": 2001,
    "createdAt": "2026-04-21 14:30:00"
  }
}
```

### 对应 DTO / VO

- `ChatSessionCreateRequest`
- `ChatSessionCreateVO`

---

## 3. 修改会话标题

### 接口

```http
POST /api/chat/session/update-title
Content-Type: application/json
```

### 请求 DTO

```json
{
  "sessionId": 1001,
  "title": "帮我做 AI 聊天网站"
}
```

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

### 对应 DTO

- `ChatSessionUpdateTitleRequest`

---

## 4. 删除会话

### 接口

```http
POST /api/chat/session/delete
Content-Type: application/json
```

### 请求 DTO

```json
{
  "sessionId": 1001
}
```

### 删除策略

第一版建议做逻辑删除：

- 更新 `chat_session.status = 0`
- 消息是否同时逻辑删除，可以后端自行决定

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

### 对应 DTO

- `ChatSessionDeleteRequest`

---

## 5. 获取会话消息列表

### 接口

```http
GET /api/chat/message/list?sessionId=1001
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| sessionId | Long | 是 | 会话 ID |

如果后面消息很多，可再加：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| pageNo | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 页大小 |

### 响应 VO

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": 1001,
    "title": "帮我做 AI 聊天网站",
    "modeCode": "quick",
    "defaultModelId": 2001,
    "messageList": [
      {
        "messageId": 9001,
        "role": "user",
        "content": "帮我设计一套数据库",
        "contentFormat": "markdown",
        "seqNo": 1,
        "modelId": null,
        "modelCode": null,
        "modelName": null,
        "finishReason": null,
        "status": 1,
        "promptTokens": 0,
        "completionTokens": 0,
        "totalTokens": 0,
        "createdAt": "2026-04-21 14:31:00"
      },
      {
        "messageId": 9002,
        "role": "assistant",
        "content": "可以，建议先设计用户表、会话表、消息表...",
        "contentFormat": "markdown",
        "seqNo": 2,
        "modelId": 2001,
        "modelCode": "deepseek-v4-flash",
        "modelName": "DeepSeek V4 Flash",
        "finishReason": "stop",
        "status": 1,
        "promptTokens": 500,
        "completionTokens": 800,
        "totalTokens": 1300,
        "createdAt": "2026-04-21 14:31:05"
      }
    ]
  }
}
```

### 对应 VO

- `ChatMessageItemVO`
- `ChatMessageListVO`

---

## 6. 发送消息并流式返回

这是聊天网站最核心的接口。

### 接口

```http
POST /api/chat/message/send
Content-Type: application/json
Accept: text/event-stream
```

### 请求 DTO

```json
{
  "sessionId": 1001,
  "content": "帮我设计 Spring Boot 的 Entity 和 Mapper",
  "modelId": 2001,
  "modeCode": "quick",
  "systemPrompt": "请优先给出可以直接落地的 Java 代码结构",
  "attachmentIds": [3001],
  "enableDeepThinking": false,
  "enableWebSearch": false,
  "regenerateMessageId": null
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| sessionId | Long | 是 | 会话 ID |
| content | String | 是 | 用户输入内容 |
| modelId | Long | 否 | 本次指定模型，不传则取会话默认模型 |
| modeCode | String | 否 | `quick` / `expert`，不传则沿用会话，并据此加载默认提示词模板 |
| systemPrompt | String | 否 | 本次附加提示词，和模式模板合并使用 |
| attachmentIds | List<Long> | 否 | 已上传附件 ID 列表，支持图片附件，按用户选择顺序传入 |
| enableDeepThinking | Boolean | 否 | 预留字段 |
| enableWebSearch | Boolean | 否 | 是否启用联网搜索；开启后服务端需先检索，再把结果摘要注入模型上下文 |
| regenerateMessageId | Long | 否 | 若是重新生成，传上一条 assistant 消息 ID |

### 服务端建议处理流程

1. 校验 `sessionId` 属于当前用户
2. 校验模型是否可用
3. 校验 `attachmentIds` 是否都属于当前用户且已完成上传
4. 如果 `enableWebSearch = true`，先执行联网检索并拿到标准化结果摘要
5. 根据 `modeCode` 解析模式默认提示词模板
6. 合并模式提示词、会话级提示词、本次请求级提示词和联网搜索结果摘要
7. 先落库用户消息
8. 创建一条 assistant 占位消息，状态可先记为生成中
9. 调用模型接口并逐段返回
10. 流结束后更新 assistant 完整内容、token、finishReason
11. 记录 `api_call_log`
12. 记录 `user_token_usage`
13. 更新 `chat_session.lastMessageAt`

### 联网搜索第一版建议

- 先抽象 `WebSearchService`，不要把具体搜索供应商直接写死在聊天 service 中
- 第一版只要求把搜索结果摘要注入模型上下文，不要求单独做搜索结果页
- 搜索结果建议保留：
  - 标题
  - 摘要
  - 来源 URL
  - 抓取时间
- 后续如果需要前端展示引用来源，再把这些结果透给消息元数据

---

## 7. 文件上传与图片上传

这部分能力用于给聊天消息补充图片或通用附件。

### 能力目标

- 支持聊天输入区上传图片
- 支持后续扩展普通文件上传
- 上传完成后返回 `fileId`，供 `/api/chat/message/send` 通过 `attachmentIds` 引用
- 第一版实现收敛为单接口非断点续传上传，不再维护 `init/chunk/complete` 分片协议

### 上传范围建议

- 图片优先支持：`jpg`、`jpeg`、`png`、`webp`
- 单文件大小由服务端统一限制，当前建议 `<= 5MB`
- 发送聊天消息时，仅允许引用当前用户已上传完成的附件

### 7.1 直传图片

```http
POST /api/file/upload/image
Content-Type: multipart/form-data
```

表单字段建议：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| file | Binary | 是 | 单张图片文件 |

响应建议：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": 3001,
    "fileName": "diagram.png",
    "contentType": "image/png",
    "fileSize": 5242880,
    "fileUrl": "/api/file/content/3001",
    "thumbnailUrl": "/api/file/content/3001"
  }
}
```

### 7.2 读取图片内容

```http
GET /api/file/content/{fileId}
```

说明：

- 当前通过后端读取本地已保存图片，用于聊天输入区预览和消息区回显
- 当前项目还未接入登录态，因此读取接口默认不做额外鉴权
- 等登录鉴权接入后，再把读取权限收敛到真实用户会话

---

## 8. 重新生成回答与中断后继续

如果你希望前端显式调用“重新生成”或“继续生成”，可以先复用同一个接口。

详细技术方案见：

- [chat-response-resume-design.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/chat-response-resume-design.md)

### 接口

```http
POST /api/chat/message/regenerate
Content-Type: application/json
Accept: text/event-stream
```

### 请求 DTO

```json
{
  "sessionId": 1001,
  "regenerateMessageId": 9002,
  "modelId": 2001,
  "modeCode": "expert"
}
```

### 说明

- `regenerateMessageId` 一般指向上一条 assistant 消息
- 服务端可根据该消息找到前一条 user 消息重新发起生成
- 当目标消息状态是 `INTERRUPTED` 时，可将这次请求解释为“继续生成”
- 第一版建议优先实现“中断后继续”分支，再补完整的“重新生成”分支

---

## SSE 事件协议设计

第一版建议采用带 `event` 的 SSE 协议，前端更容易区分事件类型。

### 响应头

```http
Content-Type: text/event-stream;charset=UTF-8
Cache-Control: no-cache
Connection: keep-alive
```

### 事件类型建议

- `message_start`
- `message_delta`
- `message_end`
- `message_error`
- `heartbeat`

### 1. message_start

表示 assistant 消息开始生成。

```text
event: message_start
data: {"sessionId":1001,"messageId":9003,"role":"assistant","modelId":2001,"modelCode":"deepseek-v4-flash","modelName":"DeepSeek V4 Flash"}
```

### 2. message_delta

表示返回增量文本片段。

```text
event: message_delta
data: {"messageId":9003,"delta":"下面我先帮你整理 Entity 和 Mapper 的结构。"}
```

### 3. message_end

表示本次消息生成完成。

```text
event: message_end
data: {
  "messageId": 9003,
  "finishReason": "stop",
  "promptTokens": 520,
  "completionTokens": 900,
  "totalTokens": 1420,
  "createdAt": "2026-04-21 15:01:00"
}
```

### 4. message_error

表示本次流式生成失败。

```text
event: message_error
data: {"messageId":9003,"errorCode":"MODEL_TIMEOUT","errorMessage":"模型响应超时"}
```

### 5. heartbeat

如果生成较长，后端可以定期发心跳。

```text
event: heartbeat
data: {"timestamp":"2026-04-21 15:01:00"}
```

## SSE 前端处理建议

前端收到后建议这样处理：

1. `message_start`
   创建 assistant 消息气泡
2. `message_delta`
   持续拼接内容
3. `message_end`
   标记完成，更新 token 信息
4. `message_error`
   标记失败并展示错误

---

## DTO 设计

下面给出建议的 Java DTO / VO 结构。

## 通用响应 DTO

### CommonResponse

```java
package com.example.aichat.common.dto;

import lombok.Data;

@Data
public class CommonResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        CommonResponse<T> response = new CommonResponse<>();
        response.setCode(0);
        response.setMessage("success");
        response.setData(data);
        return response;
    }
}
```

### PageResponse

```java
package com.example.aichat.common.dto;

import java.util.List;
import lombok.Data;

@Data
public class PageResponse<T> {

    private List<T> list;
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
}
```

## 会话相关 DTO / VO

### ChatSessionCreateRequest

```java
package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionCreateRequest {

    private String title;
    private String modeCode;
    private Long defaultModelId;
    private String systemPrompt;
}
```

说明：

- `modeCode` 用于决定默认提示词模板
- `systemPrompt` 是会话级补充提示词，不建议直接替代模板

### ChatSessionUpdateTitleRequest

```java
package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionUpdateTitleRequest {

    private Long sessionId;
    private String title;
}
```

### ChatSessionDeleteRequest

```java
package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatSessionDeleteRequest {

    private Long sessionId;
}
```

### ChatSessionCreateVO

```java
package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatSessionCreateVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private LocalDateTime createdAt;
}
```

### ChatSessionListItemVO

```java
package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatSessionListItemVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private String defaultModelCode;
    private String defaultModelName;
    private String lastMessagePreview;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
```

## 消息相关 DTO / VO

### ChatMessageSendRequest

```java
package com.example.aichat.modules.chat.dto;

import java.util.List;
import lombok.Data;

@Data
public class ChatMessageSendRequest {

    private Long sessionId;
    private String content;
    private Long modelId;
    private String modeCode;
    private String systemPrompt;
    private List<Long> attachmentIds;
    private Boolean enableDeepThinking;
    private Boolean enableWebSearch;
    private Long regenerateMessageId;
}
```

说明：

- `modeCode` 决定本次请求使用哪套模式模板
- `systemPrompt` 是本次请求附加提示词
- `attachmentIds` 引用已上传完成的文件或图片
- 最终发给模型的系统提示词由后端统一组装

### ChatMessageRegenerateRequest

```java
package com.example.aichat.modules.chat.dto;

import lombok.Data;

@Data
public class ChatMessageRegenerateRequest {

    private Long sessionId;
    private Long regenerateMessageId;
    private Long modelId;
    private String modeCode;
}
```

### ChatMessageItemVO

```java
package com.example.aichat.modules.chat.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatMessageItemVO {

    private Long messageId;
    private String role;
    private String content;
    private String contentFormat;
    private Integer seqNo;
    private Long modelId;
    private String modelCode;
    private String modelName;
    private String finishReason;
    private Integer status;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
```

### ChatMessageListVO

```java
package com.example.aichat.modules.chat.vo;

import java.util.List;
import lombok.Data;

@Data
public class ChatMessageListVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private List<ChatMessageItemVO> messageList;
}
```

## SSE 事件 DTO

### ChatStreamStartEvent

```java
package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamStartEvent {

    private Long sessionId;
    private Long messageId;
    private String role;
    private Long modelId;
    private String modelCode;
    private String modelName;
}
```

### ChatStreamDeltaEvent

```java
package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamDeltaEvent {

    private Long messageId;
    private String delta;
}
```

### ChatStreamEndEvent

```java
package com.example.aichat.modules.chat.dto.stream;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ChatStreamEndEvent {

    private Long messageId;
    private String finishReason;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
```

### ChatStreamErrorEvent

```java
package com.example.aichat.modules.chat.dto.stream;

import lombok.Data;

@Data
public class ChatStreamErrorEvent {

    private Long messageId;
    private String errorCode;
    private String errorMessage;
}
```

## Controller 设计建议

建议拆成两个控制器：

### ChatSessionController

负责：

- 创建会话
- 查询会话列表
- 修改会话标题
- 删除会话

### ChatMessageController

负责：

- 查询消息列表
- 发送消息
- 重新生成回答
- SSE 输出

---

## Service 方法建议

### ChatSessionService

```java
public interface ChatSessionService {

    PageResponse<ChatSessionListItemVO> listUserSessions(Long userId, Integer pageNo, Integer pageSize);

    ChatSessionCreateVO createSession(Long userId, ChatSessionCreateRequest request);

    void updateTitle(Long userId, ChatSessionUpdateTitleRequest request);

    void deleteSession(Long userId, Long sessionId);
}
```

### ChatMessageService

```java
public interface ChatMessageService {

    ChatMessageListVO listMessages(Long userId, Long sessionId);

    void sendMessage(Long userId, ChatMessageSendRequest request, SseEmitter emitter);

    void regenerateMessage(Long userId, ChatMessageRegenerateRequest request, SseEmitter emitter);
}
```

## 模式提示词解析建议

建议在聊天服务中增加一个独立的提示词解析组件，例如：

```java
public interface ChatPromptResolver {

    String resolveSystemPrompt(String modeCode, String sessionPrompt, String requestPrompt);
}
```

推荐职责：

- 根据 `modeCode` 找到默认模板
- 合并会话级和请求级补充提示词
- 输出最终发送给模型的系统提示词

## 推荐的第一版实现方式

第一版为了简单，可先把模式模板写在配置文件或枚举中，不一定马上建表。

例如：

```yaml
chat:
  mode-prompts:
    quick: |
      你是一个高效、直接的 AI 助手。
      请优先给出结论，再补充必要说明。
    expert: |
      你是一个专业顾问型 AI 助手。
      请先分析问题，再给出结构化答案。
```

后续如果需要后台可配置，再升级为数据库表。

## 后续可扩展方案

如果你后面想让运营后台可改模板，建议新增：

- `prompt_template` 表

每种模式至少维护一条默认模板，例如：

- `quick_default`
- `expert_default`

也可以继续扩展：

- 按场景区分模板
- 按模型区分模板
- 按租户区分模板

---

## 状态与枚举建议

建议统一定义这些值，避免前后端写死：

### ChatModeEnum

- `quick`
- `expert`

### ChatRoleEnum

- `system`
- `user`
- `assistant`

### MessageStatusEnum

- `1` 正常
- `0` 失败
- `2` 中断
- `3` 生成中

### FinishReasonEnum

- `stop`
- `length`
- `error`
- `cancel`

---

## 错误码建议

第一版建议先统一几类错误：

当前普通 JSON 接口响应中，`code` 仍保持数值状态码，`message` 返回中文错误提示，方便前端直接展示。

| 错误码 | 说明 |
|---|---|
| `INVALID_PARAM` | 请求参数校验失败 |
| `REQUEST_BODY_INVALID` | 请求体、查询参数或路径参数格式错误 |
| `CHAT_SESSION_NOT_FOUND` | 会话不存在 |
| `CHAT_SESSION_NO_PERMISSION` | 无权访问该会话 |
| `CHAT_MODEL_NOT_FOUND` | 模型不存在 |
| `CHAT_MODEL_DISABLED` | 模型已禁用 |
| `CHAT_DEFAULT_MODEL_NOT_CONFIGURED` | 默认模型未配置或未启用 |
| `CHAT_MODEL_CLIENT_NOT_FOUND` | 未找到可用模型客户端 |
| `CHAT_MESSAGE_EMPTY` | 消息内容为空 |
| `CHAT_STREAM_ERROR` | 流式输出异常 |
| `CHAT_REGENERATE_NOT_IMPLEMENTED` | 重新生成接口暂未实现 |
| `DEEPSEEK_API_KEY_NOT_CONFIGURED` | DeepSeek API Key 未配置 |
| `MODEL_REQUEST_FAILED` | 模型调用失败 |
| `MODEL_TIMEOUT` | 模型响应超时 |
| `INTERNAL_ERROR` | 未预期服务端异常 |

---

## 第一版实现建议

为了尽快联调，建议实现顺序如下：

1. `GET /session/list`
2. `POST /session/create`
3. `GET /message/list`
4. `POST /message/send` + SSE
5. `POST /session/update-title`
6. `POST /session/delete`
7. `POST /file/upload/image`
8. `GET /file/content/{fileId}`
9. `POST /message/regenerate`

最先打通的主链路应该是：

1. 创建会话
2. 发送消息
3. SSE 返回回答
4. 刷新后还能看到历史会话和消息

---

## 当前文档的默认假设

这份设计默认你当前做的是第一版 MVP，因此我做了这些取舍：

- “发消息”优先做成一个流式接口，而不是同步接口
- `DTO / VO` 保持够用，不提前过度抽象
- 图片上传和聊天响应中断后继续都已进入实现阶段，但仍按独立能力迭代和验证
- 其他多模态、搜索先预留字段，不进入主流程
- 会话删除按逻辑删除考虑
- 重新生成能力保留单独接口，也可后续合并

如果你下一步继续往下做，最顺手的是这两项之一：

1. 我直接帮你补 `Controller + Service + DTO/VO` 的 Java 代码骨架
2. 我继续补 `前端聊天页接口对接说明`，把 Vue 调用方式和 SSE 接法一起整理出来
