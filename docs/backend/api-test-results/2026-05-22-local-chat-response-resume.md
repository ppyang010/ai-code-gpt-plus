# 2026-05-22 local chat-response-resume 联调结果

## 执行信息

- 执行日期：`2026-05-22`
- 执行环境：`local`
- 执行主题：`chat-response-resume`
- 对应用例：
  - `5. 创建会话`
  - `6. 查询消息列表`
  - `7. 发送消息`
  - `10. 中断后继续生成`

## 结果摘要

- 总体结果：`通过`
- 结论：
  - 创建会话接口验证通过
  - 流式发送后主动中断，assistant 消息会保留部分内容
  - 中断后的 assistant 消息状态会落到 `INTERRUPTED`
  - `POST /api/chat/message/regenerate` 会复用原 assistant 消息继续生成
  - 继续生成完成后 assistant 消息状态恢复为完成态
  - 前端本地开发入口可访问，`/api/model/list` 可通过 Vite 代理访问后端
  - 中断请求不会再误记 `api_call_log` 失败记录，也不会再产生 SSE 全局异常告警

## 详细记录

### 用例 5：创建会话

- 请求结果：
  - 返回 `code = 0`
  - 成功创建测试会话：`sessionId = 9`
  - `modeCode = quick`
  - `defaultModelId = 1`
- 副作用结果：
  - `chat_session` 新增一条测试会话记录
- 问题与后续动作：
  - 无

### 用例 7：发送消息后主动中断

- 请求结果：
  - 返回 `text/event-stream`
  - 实际收到事件顺序：
    - `message_start`
    - `message_delta`
    - `message_delta`
    - `message_delta`
  - 收到 3 个 `message_delta` 后主动中断请求
- 副作用结果：
  - 新增 `user message`：`messageId = 25`
  - 新增 `assistant message`：`messageId = 26`
  - 中断后 assistant 消息状态：`status = 2`
  - 中断后 assistant 消息 `finishReason = interrupted`
  - 中断后 assistant 内容长度：`8`
  - 中断内容前缀：`第1行：这是一次`
- 问题与后续动作：
  - 当前验证主要覆盖接口与状态流转，浏览器层页面点击联调仍待补

### 用例 10：中断后继续生成

- 请求结果：
  - 调用 `POST /api/chat/message/regenerate` 成功
  - 返回 `text/event-stream`
  - 实际收到：
    - `message_start`
    - `184` 个 `message_delta`
    - `message_end`
- 副作用结果：
  - 继续生成复用原 assistant 消息：`messageId = 26`
  - 完成后 assistant 消息状态：`status = 1`
  - 完成后 assistant 消息 `finishReason = stop`
  - 完成后 assistant 内容长度：`292`
  - 内容在原中断前缀后继续追加，没有新建第二条 assistant 消息
- 问题与后续动作：
  - 建议后续补浏览器层联调，确认停止生成、刷新回显和继续生成按钮展示与接口状态一致

### 补充检查：前端入口与代理冒烟验证

- 请求结果：
  - `http://127.0.0.1:5173/` 返回前端入口 HTML
  - `http://127.0.0.1:5173/api/model/list` 返回 `code = 0`
  - 模型列表包含：
    - `deepseek-v4-flash`
    - `deepseek-v4-pro`
- 副作用结果：
  - 无写库副作用
- 问题与后续动作：
  - 当前仅完成前端入口和代理冒烟验证，未完成浏览器自动化点击验证

### 补充检查：`api_call_log` 落库结果

- 请求结果：
  - 查询 `session_id = 9` 的最新调用日志
  - 仅发现一条成功记录：`id = 12`
  - `message_id = 26`
  - `success_flag = 1`
  - `response_payload_valid = 1`
- 副作用结果：
  - 没有额外写入一条 `success_flag = 0` 的失败日志
  - 中断请求未再触发非法 JSON 写入
- 问题与后续动作：
  - 无
