# 2026-05-15 local frontend-integration 联调结果

## 执行信息

- 执行日期：`2026-05-15`
- 执行环境：`local`
- 执行主题：`frontend-integration`
- 对应用例：
  - `2. 查询会话列表`
  - `3. 创建会话`
  - `4. 查询消息列表`
  - `5. 发送消息`

## 结果摘要

- 总体结果：`通过`
- 结论：
  - 会话列表接口验证通过
  - 创建会话接口验证通过
  - 消息列表接口验证通过
  - 发送消息接口验证通过
  - SSE 增量返回验证通过
  - `chat_message`、`api_call_log`、`user_token_usage` 落库验证通过

## 详细记录

### 用例 2：查询会话列表

- 请求结果：
  - 返回 `code = 0`
  - 返回已有会话列表，包含 `sessionId = 1` 和 `sessionId = 2`
- 副作用结果：
  - 无写库副作用
- 问题与后续动作：
  - 无

### 用例 3：创建会话

- 请求结果：
  - 返回 `code = 0`
  - 成功创建会话：`sessionId = 3`
  - `title = 前端真实联调`
  - `modeCode = quick`
  - `defaultModelId = 1`
- 副作用结果：
  - `chat_session` 新增一条记录
- 问题与后续动作：
  - 无

### 用例 4：查询消息列表

- 请求结果：
  - 返回 `code = 0`
  - 新会话初始返回 `messageList = []`
- 副作用结果：
  - 无写库副作用
- 问题与后续动作：
  - 无

### 用例 5：发送消息

- 请求结果：
  - 返回 `text/event-stream`
  - 按顺序收到：
    - `message_start`
    - 多个 `message_delta`
    - `message_end`
  - 最终内容：`前端真实联调已完成。`
- SSE 实际事件顺序：
  - `message_start`
  - `message_delta`
  - `message_delta`
  - `message_delta`
  - `message_delta`
  - `message_delta`
  - `message_delta`
  - `message_end`
- 副作用结果：
  - `chat_message` 新增两条消息：
    - `role = user`
    - `role = assistant`
  - assistant 消息 token：
    - `total_tokens = 46`
  - `api_call_log` 新增成功记录：
    - `success_flag = 1`
    - `total_tokens = 46`
  - `user_token_usage` 新增记录：
    - `total_tokens = 46`
- 问题与后续动作：
  - 前端页面仍需补失败重试、错误提示和 Markdown 渲染体验
