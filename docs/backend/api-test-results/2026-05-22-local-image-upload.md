# 2026-05-22 local image-upload 联调结果

## 执行信息

- 执行日期：`2026-05-22`
- 执行环境：`local`
- 执行主题：`image-upload`
- 对应用例：
  - `10. 图片直传上传与附件发送`

## 结果摘要

- 总体结果：`通过`
- 结论：
  - `POST /api/file/upload/image` 直传图片成功
  - 上传完成后返回 `fileId`、`fileUrl` 和 `thumbnailUrl`
  - `attachmentIds` 可随 `POST /api/chat/message/send` 一起提交
  - 消息列表中的 user message 可返回附件元数据

## 详细记录

### 用例 10：图片直传上传

- 请求结果：
  - 成功上传测试图片：`gpt-plus-upload-test.png`
  - 返回 `fileId = 1`
  - `contentType = image/png`
  - `fileSize = 68`
  - `fileUrl = /api/file/content/1`
- 副作用结果：
  - `file_asset` 新增一条记录
- 问题与后续动作：
  - 当前仅支持 `jpg`、`jpeg`、`png`、`webp`

### 用例 10：带附件发送消息

- 请求结果：
  - 创建测试会话：`sessionId = 10`
  - 发送消息时携带 `attachmentIds = [1]`
  - SSE 实际收到：
    - `message_start`
    - 多个 `message_delta`
    - `message_end`
- 副作用结果：
  - user message：`messageId = 27`
  - `GET /api/chat/message/list?sessionId=10` 返回的 user message 中包含：
    - `fileId = 1`
    - `fileName = gpt-plus-upload-test.png`
    - `fileUrl = /api/file/content/1`
- 问题与后续动作：
  - 当前图片仅作为聊天附件保存和回显，尚未接入模型多模态理解
