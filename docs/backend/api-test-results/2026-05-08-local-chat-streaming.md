# 2026-05-08 local chat-streaming 联调结果

## 执行信息

- 执行日期：`2026-05-08`
- 执行环境：`local`
- 执行主题：`chat-streaming`
- 对应用例：
  - `2. 查询会话列表`
  - `3. 创建会话`
  - `5. 发送消息`

## 结果摘要

- 总体结果：`通过`
- 结论：
  - 后端可以编译通过
  - Spring Boot 服务可以启动成功
  - 会话列表接口验证通过
  - 真实 DeepSeek SSE 增量输出验证通过
  - `chat_message`、`api_call_log`、`user_token_usage` 落库验证通过

## 详细记录

### 用例 2：查询会话列表

- 请求结果：
  - 返回 `code = 0`
  - 返回空分页结果：`list = []`、`total = 0`
- 副作用结果：
  - 无写库副作用
- 问题与后续动作：
  - 无

### 用例 3：创建会话

- 请求结果：
  - 返回 `code = 0`
  - 成功创建会话：`sessionId = 1`
  - `title = DeepSeek streaming test`
  - `modeCode = quick`
  - `defaultModelId = 1`
- 副作用结果：
  - `chat_session` 新增一条记录
- 问题与后续动作：
  - 当前创建前提依赖库里已有 `deepseek-chat` 模型配置，后续需补模型初始化数据方案

### 用例 5：发送消息

- 请求结果：
  - 返回 `text/event-stream`
  - 按顺序收到：
    - `message_start`
    - 多个 `message_delta`
    - `message_end`
  - 真实返回内容为逐段中文增量输出，不是一次性 mock 文本
- SSE 实际事件顺序：
  - `message_start`
  - 多个 `message_delta`
  - `message_end`
- 副作用结果：
  - `chat_message` 新增两条消息：
    - `role = user`
    - `role = assistant`
  - assistant 消息 token：
    - `prompt_tokens = 44`
    - `completion_tokens = 21`
    - `total_tokens = 65`
  - `api_call_log` 新增成功记录：
    - `success_flag = 1`
    - `http_status = 200`
    - `total_tokens = 65`
  - `user_token_usage` 新增记录：
    - `total_tokens = 65`
    - `stat_date = 2026-05-08`
- 问题与后续动作：
  - 后续应补更多接口结果文件，而不是继续把执行结论写回 `cases`
