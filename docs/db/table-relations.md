# AI Chat MVP 数据库表关联说明

## 说明

- 当前版本数据库 **不创建外键约束**。
- 表之间的关联关系通过字段命名和应用层校验维护。
- 这样做的目的是减少首版开发和迁移成本，避免外键影响批量写入、日志落库和历史数据修复。
- 后端服务需要在新增、更新、删除时主动校验关联数据是否合法。

## 核心表清单

- `app_user`：用户表
- `model_provider`：模型供应商表
- `model_config`：模型配置表
- `chat_session`：会话表
- `chat_message`：消息表
- `api_call_log`：模型调用日志表
- `user_balance`：用户余额表，首版只预留
- `user_token_usage`：用户 token 消耗表，首版只记录
- `admin_user`：后台管理用户表
- `admin_login_log`：后台登录日志表
- `prompt_template`：附加提示词模板表

## 表关联关系

### 1. 用户与会话

- `chat_session.user_id -> app_user.id`
- 关系：一个用户可以有多个会话
- 使用场景：查询某个用户的历史会话列表

### 2. 用户与消息

- `chat_message.user_id -> app_user.id`
- 关系：一个用户可以有多条消息
- 使用场景：用户维度查询消息、做数据审计

### 3. 会话与消息

- `chat_message.session_id -> chat_session.id`
- 关系：一个会话可以有多条消息
- 使用场景：进入会话后按 `seq_no` 顺序拉取聊天记录

### 4. 模型供应商与模型配置

- `model_config.provider_id -> model_provider.id`
- 关系：一个供应商可以配置多个模型
- 使用场景：按供应商展示模型列表，统一适配不同厂商接口

### 5. 会话与默认模型

- `chat_session.default_model_id -> model_config.id`
- 关系：一个会话默认绑定一个模型，可为空
- 使用场景：会话打开时回显默认模型，便于继续对话

### 6. 消息与模型

- `chat_message.model_id -> model_config.id`
- 关系：一条消息可记录本次使用的模型
- 使用场景：同一会话中切换模型后，仍能追溯每条回答由哪个模型生成

### 7. 模型调用日志与用户

- `api_call_log.user_id -> app_user.id`
- 关系：一个用户可以有多条调用日志
- 使用场景：统计某个用户的调用量、排查异常请求

### 8. 模型调用日志与会话

- `api_call_log.session_id -> chat_session.id`
- 关系：一条调用日志可关联一个会话
- 使用场景：排查某个会话中的模型调用情况

### 9. 模型调用日志与消息

- `api_call_log.message_id -> chat_message.id`
- 关系：一条调用日志可关联一条消息
- 使用场景：将一次模型调用和最终生成消息关联起来

### 10. 模型调用日志与供应商/模型

- `api_call_log.provider_id -> model_provider.id`
- `api_call_log.model_id -> model_config.id`
- 关系：每次调用都明确记录供应商和模型
- 使用场景：统计不同模型的耗时、成功率、成本

### 11. 用户余额与用户

- `user_balance.user_id -> app_user.id`
- 关系：一个用户对应一条余额记录
- 使用场景：后续扩展充值、扣费、套餐
- 当前阶段：只预留，不做调用拦截

### 12. 用户 token 消耗与用户

- `user_token_usage.user_id -> app_user.id`
- 关系：一个用户可以有多条 token 消耗记录
- 使用场景：做用户维度的日统计、成本分析

### 13. 用户 token 消耗与会话/消息/调用日志

- `user_token_usage.session_id -> chat_session.id`
- `user_token_usage.message_id -> chat_message.id`
- `user_token_usage.api_call_log_id -> api_call_log.id`
- 使用场景：把一次 token 消耗和具体会话、消息、调用日志关联起来

### 14. 用户 token 消耗与供应商/模型

- `user_token_usage.provider_id -> model_provider.id`
- `user_token_usage.model_id -> model_config.id`
- 使用场景：按模型统计用户的 token 使用量和预估成本

### 15. 后台管理员与登录日志

- `admin_login_log.admin_user_id -> admin_user.id`
- 关系：一个后台管理员可以对应多条登录日志，失败日志允许为空管理员 ID
- 使用场景：后台登录审计、失败排查和最近登录时间追踪

### 16. 附加提示词模板

- `prompt_template` 当前为独立配置表，不依赖其他主表
- 关系：模板通过 `template_code` 和 `template_scope` 在应用层选择与启停
- 使用场景：后台维护通用附加提示词模板，后续由 `ChatPromptResolver` 按规则读取

## 推荐的应用层约束

因为当前不使用外键，建议后端至少实现下面这些校验：

- 创建 `chat_session` 时，校验 `user_id` 是否存在
- 创建 `chat_message` 时，校验 `session_id` 是否存在且属于当前用户
- 写入 `chat_message.model_id` 时，校验模型是否启用
- 写入 `model_config.provider_id` 时，校验供应商是否存在
- 写入 `api_call_log` 时，校验 `user_id`、`provider_id`、`model_id` 的合法性
- 写入 `user_balance` 时，保证每个用户只有一条余额记录
- 写入 `user_token_usage` 时，尽量带上 `api_call_log_id` 方便后续追踪
- 写入 `admin_login_log` 时，失败场景允许 `admin_user_id` 为空，但需要保留 `login_username`
- 写入 `prompt_template` 时，保证 `template_code` 全局唯一，`template_content` 非空
- 启用 `prompt_template` 前，校验 `template_scope` 是否属于系统支持的模板作用域

## 推荐查询路径

### 查询用户会话列表

- `app_user`
- `chat_session`

### 查询某个会话的聊天记录

- `chat_session`
- `chat_message`
- 可选关联 `model_config`

### 查询某次消息背后的模型调用日志

- `chat_message`
- `api_call_log`
- `model_provider`
- `model_config`

### 统计用户成本和 token 消耗

- `user_token_usage`
- `api_call_log`
- `model_config`

### 查询后台登录审计

- `admin_user`
- `admin_login_log`

### 查询附加提示词模板

- `prompt_template`

## 当前已接入与后续可扩展表

当前已接入：

- `file_asset`：聊天图片附件上传、预览和消息引用

已在数据库文档中补齐、等待后续代码接入的表：

- `admin_user`：后台管理用户
- `admin_login_log`：后台登录日志
- `prompt_template`：通用附加提示词模板

后续如果要继续迭代，建议补充这些表：

- `recharge_order`：充值订单
