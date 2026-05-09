# 后端接口测试用例

最后更新：2026-05-09

## 说明

- 本文档用于记录当前项目后端接口的本地联调测试用例。
- 本文档的维护方式遵循 [api-test-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/api-test-rules.md)。
- 实际执行结果单独维护在 [api-test-results/README.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/api-test-results/README.md)。
- 默认后端项目目录为：`/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core`
- 默认通过 `jenv exec` 启动后端，避免误用本机全局 JDK 8。
- 默认服务地址为：`http://127.0.0.1:8080`

## 启动后端

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn spring-boot:run -Dspring-boot.run.profiles=local
```

启动成功后，日志中应看到：

- `Started GptPlusCoreApplication`
- `Tomcat started on port 8080`

## 测试前提

- 本地 MySQL 已启动
- 数据源已配置到 [application.yml](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/resources/application.yml)
- 数据库初始化 SQL 已执行
- 当前临时用户身份通过请求头 `X-User-Id` 传入
- 如果要验证真实 DeepSeek 调用，需保证 `model_provider` / `model_config` 中已有启用的 `deepseek` / `deepseek-chat` 配置

## 测试用例

### 1. 服务可访问性检查

#### 请求

```bash
curl -i -s "http://127.0.0.1:8080/"
```

#### 预期

- 返回 HTTP `200`
- 返回 JSON 结构
- 当前因为没有首页资源，响应内容可能是：

```json
{"code":500,"data":null,"message":"No static resource  for request '/'."}
```

说明：

- 这不表示服务启动失败
- 只表示根路径 `/` 没有配置页面或静态资源

### 2. 查询会话列表

#### 请求

```bash
curl -s "http://127.0.0.1:8080/api/chat/session/list?pageNo=1&pageSize=10" \
  -H "X-User-Id: 1"
```

#### 预期

- 返回 `code = 0`
- 返回分页结构
- 当数据库中没有会话时，返回示例：

```json
{
  "code": 0,
  "data": {
    "list": [],
    "pageNo": 1,
    "pageSize": 10,
    "total": 0
  },
  "message": "success"
}
```

### 3. 创建会话

#### 请求

```bash
curl -s -X POST "http://127.0.0.1:8080/api/chat/session/create" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "title": "测试会话",
    "modeCode": "quick"
  }'
```

#### 预期

- 返回 `code = 0`
- 返回会话 ID、标题、模式、创建时间
- 响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": 1,
    "title": "测试会话",
    "modeCode": "quick",
    "defaultModelId": null,
    "createdAt": "2026-05-05T15:00:00"
  }
}
```

#### 联调检查点

- `chat_session` 表中新增一条记录
- `user_id = 1`
- `status = 1`

### 4. 查询消息列表

#### 请求

```bash
curl -s "http://127.0.0.1:8080/api/chat/message/list?sessionId=1" \
  -H "X-User-Id: 1"
```

#### 预期

- 返回 `code = 0`
- 返回 `sessionId`、`title`、`modeCode`
- 初始情况下 `messageList` 可能为空

### 5. 发送消息

#### 请求

```bash
curl -N -X POST "http://127.0.0.1:8080/api/chat/message/send" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": 1,
    "modelId": 1,
    "content": "请用一句话确认你是 DeepSeek 真流式响应。",
    "modeCode": "quick"
  }'
```

#### 预期

- 返回 `text/event-stream`
- 应该依次收到：
  - `message_start`
  - 多个 `message_delta`
  - `message_end`

#### 接口行为说明

- 当前接口应返回真实 SSE 增量输出，而不是一次性 mock 返回
- 会先保存 `user message`
- 再保存一条 `assistant message` 占位消息
- 生成结束后会更新 assistant 内容和 token 字段
- 成功调用后会新增一条 `api_call_log`
- 成功调用后会新增一条 `user_token_usage`

#### 联调检查点

- `chat_message` 表新增两条消息：
  - 一条 `role = user`
  - 一条 `role = assistant`
- `chat_session.last_message_at` 被更新
- `api_call_log.success_flag = 1`
- `user_token_usage.total_tokens` 与 `chat_message.total_tokens` 一致

### 6. 修改会话标题

#### 请求

```bash
curl -s -X POST "http://127.0.0.1:8080/api/chat/session/update-title" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": 1,
    "title": "新的会话标题"
  }'
```

#### 预期

- 返回：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

- 数据库中 `chat_session.title` 被更新

### 7. 删除会话

#### 请求

```bash
curl -s -X POST "http://127.0.0.1:8080/api/chat/session/delete" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": 1
  }'
```

#### 预期

- 返回：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

- 数据库中 `chat_session.status` 被更新为逻辑删除状态

## 数据库辅助检查

### 查询会话

```sql
SELECT id, user_id, title, mode_code, status, last_message_at
FROM chat_session
ORDER BY id DESC;
```

### 查询消息

```sql
SELECT id, session_id, user_id, role, seq_no, model_id, status, finish_reason, created_at
FROM chat_message
ORDER BY id DESC;
```
