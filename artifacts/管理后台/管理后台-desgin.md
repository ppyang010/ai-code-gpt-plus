# 管理后台 设计文档

最后更新：2026-05-31

## 文档说明

- 本文档用于记录 GPT Plus 管理后台的目标、代码落点、设计边界和验收口径。
- 本文档负责沉淀需求定义、设计边界、代码落点和实施计划，是 `管理后台-task.md` 的设计依据。
- 配套任务跟踪文档：`管理后台-task.md`
- 配套预留功能文档：`管理后台-预留功能.md`
- 若需求目标、边界、接口、字段、数据结构、实现顺序、验收口径、任务优先级或范围出现变动，先更新本文档，再同步 `管理后台-task.md`。

## 阶段 A 结论沉淀

## 代码落点判断

- 统一入口：
  - 后端新增 `/api/admin/**` 管理接口入口，与现有 `/api/chat/**`、`/api/model/list`、`/api/file/**` 用户侧接口隔离。
  - 前端新增 `/admin/**` 管理后台路由，与现有 `/chat` 用户聊天页面隔离。
- 涉及模块：
  - 后端：`modules/admin`、`modules/model`、`modules/user`、`modules/chat`、`modules/billing`。
  - 前端：`router`、`views/admin`、`stores/admin`、`types/admin`、`lib/http`。
  - 数据库：新增 `admin_user`、`admin_login_log`、`prompt_template`，复用 `app_user`、`model_provider`、`model_config`、`api_call_log`、`user_token_usage`、`user_balance`。
- 现有可复用能力：
  - 统一响应：`CommonResponse<T>`。
  - 分页响应：`PageResponse<T>`。
  - MyBatis-Plus 基础 Mapper 和分页插件。
  - `model_provider` / `model_config` 现有模型供应商与模型配置表。
  - `api_call_log` / `user_token_usage` 现有模型调用日志与 token 用量表。
  - TDesign Vue Next 表格、表单、弹窗、分页、布局组件。
- 不建议落点：
  - 不建议把后台接口挂在现有 `/api/model/list` 下继续扩展，用户侧模型列表只应暴露可选模型和能力字段。
  - 不建议首版复用 `X-User-Id` 作为后台身份，现有请求头只是本地调试临时方案。
  - 不建议首版承诺“模型供应商 DB 修改后立即热生效”，当前 OpenAI-compatible 客户端可用性仍由本地配置判断。
- 推荐实现位置：
  - 新增 `modules/admin` 承载后台登录、管理员、后台聚合查询和后台专用 DTO/VO。
  - 模型供应商与模型配置的写操作优先落在 `modules/model` 的 admin service/controller 中，避免把模型领域逻辑拆散。
  - 普通用户管理优先复用 `modules/user` 的实体和 Mapper，在 admin controller/service 中暴露后台管理视角。
  - 提示词模板新增独立 `modules/prompt` 或 `modules/admin/prompt`。如果后续提示词能力扩大，推荐独立 `modules/prompt`。

## 需求术语映射

| 需求术语 | 代码对象 | 差异说明 |
| --- | --- | --- |
| 管理后台 | 前端 `/admin/**`、后端 `/api/admin/**` | 当前项目没有后台路由和后台接口，需要新增隔离入口 |
| 运营配置后台 | `model_provider`、`model_config`、`prompt_template` | 模型供应商和模型配置已有表，提示词模板需要新增表 |
| 模型供应商 | `ModelProviderDO` / `model_provider` | 表中有 `base_url`、`api_key_encrypted`，但首版不承诺修改后立即影响运行时 |
| 模型配置 | `ModelConfigDO` / `model_config` | 现有用户侧只读接口为 `GET /api/model/list`，后台需要新增 CRUD 和启停能力 |
| 提示词模板 | 新增 `prompt_template` / 改造 `ChatPromptResolver` | 当前聊天链路只有会话级和请求级 `systemPrompt` 合并能力，快速/思考模式后续只表示模型原生思考开关 |
| 调用日志 | `ApiCallLogDO` / `api_call_log` | 首版只读查询，不提供修改或删除 |
| 用量统计 | `UserTokenUsageDO` / `user_token_usage` | 首版只读查询和聚合统计，不做扣费拦截 |
| 管理用户 | 新增 `admin_user` | 不复用普通用户表，后台身份独立 |
| 普通用户管理 | `AppUserDO` / `app_user` | 可做列表、详情、状态调整；不在首版实现完整注册登录链路 |

## 风险与边界结论

- 权限边界：
  - 后台身份必须独立于普通用户，首版新增 `admin_user` 和后台登录 token。
  - `/api/admin/**` 必须统一校验后台 token，避免普通用户通过聊天侧身份访问后台。
- 配置生效边界：
  - 首版后台可维护 `model_provider` / `model_config` 数据库配置。
  - 首版不承诺供应商密钥、baseUrl 修改后立即影响模型调用运行时；后续可追加“动态配置加载和刷新”专项。
- 密钥安全：
  - 后台列表和详情不返回完整 API Key，只返回是否已配置、脱敏片段和更新时间。
  - 写入 `api_key_encrypted` 前需要统一加密或至少预留加密服务接口，避免明文落库。
- 审计边界：
  - 管理员登录需要落 `admin_login_log`。
  - 模型、提示词、用户状态调整属于高风险操作，建议后续补 `admin_operation_log`；首版可先在日志中记录操作人、对象和结果。
- 普通用户边界：
  - 普通用户管理首版只做管理视角，不改变用户侧登录链路。
  - 禁用普通用户后，需要后续配合用户侧鉴权才能真正阻断聊天访问；在当前 `X-User-Id` 临时方案下只能完成数据状态维护。

## 当前未确认点 / 暂不处理范围

- 暂不做模型供应商配置热刷新。
- 暂不做角色权限矩阵，首版只有管理员身份。
- 暂不做充值订单管理和支付闭环。
- 暂不做余额扣减和额度拦截，只做余额/用量只读展示。
- 暂不做后台操作日志表，除非实现中确认需要强审计落库。
- 暂不修改现有 Maven 编译和启动流程；本规划阶段只做静态设计。
- 预留但首版不做的能力统一沉淀在 `管理后台-预留功能.md`，不进入当前 `管理后台-task.md` 的 P0-P3 实现任务。

## 方案设计与实施计划

### 1. 背景

当前项目已经完成用户侧 AI 聊天主链路，包含会话、消息、模型调用、联网搜索、图片上传、调用日志和 token 用量落库。随着 OpenAI-compatible 多供应商能力接入，模型供应商、模型配置、提示词模板、调用日志和用量统计需要一个后台维护和观测入口。同时，项目还缺少后台管理员身份和普通用户管理能力。

首版管理后台定位为“运营配置后台”，范围包括模型供应商管理、模型配置管理、提示词模板管理、调用日志/用量只读、管理用户管理、普通用户管理。

### 2. 功能清单

- 后台登录：
  - 管理员账号密码登录。
  - 返回后台访问 token。
  - 后台登录日志落库。
- 管理用户管理：
  - 管理员列表、详情、新增、编辑、启停。
  - 密码初始化或重置。
- 普通用户管理：
  - 普通用户列表、详情、启停。
  - 查看用户余额、最近调用、token 用量概览。
- 模型供应商管理：
  - 供应商列表、详情、新增、编辑、启停、排序。
  - API Key 写入时加密保存，查询时脱敏。
  - 首版展示“配置修改后不保证立即影响运行时”的状态说明。
- 模型配置管理：
  - 模型列表、详情、新增、编辑、启停、排序。
  - 维护模型类型、能力字段、上下文窗口、最大输出 token、默认 temperature/top_p。
- 提示词模板管理：
  - 通用附加提示词模板列表、详情、编辑、启停。
  - 改造 `ChatPromptResolver` 从 DB 读取启用模板，DB 缺失时回退到会话级 / 请求级附加提示词直传能力。
- 调用日志只读：
  - 按用户、模型、供应商、成功状态、时间范围分页查询。
  - 查看错误码、错误信息、token、耗时、请求摘要和响应摘要。
- 用量统计只读：
  - 按用户、模型、供应商、日期范围聚合 token 和预估成本。
  - 用户详情页展示余额和近期用量。

### 3. 各模块详细设计

#### 3.1 后台身份模块

- 新增表：
  - `admin_user`：后台管理员账号。
  - `admin_login_log`：后台登录日志。
- 后端落点：
  - `modules/admin/controller/AdminAuthController`
  - `modules/admin/controller/AdminUserController`
  - `modules/admin/service/AdminAuthService`
  - `modules/admin/service/AdminUserService`
  - `modules/admin/entity/AdminUserDO`
  - `modules/admin/entity/AdminLoginLogDO`
- 认证方式：
  - 首版使用登录接口签发后台 token。
  - 新增后台 token 解析拦截器或过滤器，只拦截 `/api/admin/**`。
  - 密码存储使用哈希字段，不保存明文。

#### 3.2 管理用户管理

- 管理用户字段建议：
  - `username`、`password_hash`、`nickname`、`email`、`mobile`、`status`、`last_login_at`。
- 能力边界：
  - 首版不做细粒度角色权限。
  - 管理员启停影响后台登录，不影响普通用户侧。

#### 3.3 普通用户管理

- 复用表：
  - `app_user`
  - `user_balance`
  - `user_token_usage`
  - `api_call_log`
- 后端落点：
  - `modules/admin/controller/AdminAppUserController`
  - `modules/admin/service/AdminAppUserService`
  - 必要时复用或扩展 `modules/user` Mapper。
- 能力边界：
  - 普通用户禁用只维护 `app_user.status`。
  - 在用户侧鉴权未完成前，禁用状态对现有 `X-User-Id` 临时调用无法形成完整阻断。

#### 3.4 模型供应商管理

- 复用表：
  - `model_provider`
- 后端落点：
  - `modules/model/controller/admin/AdminModelProviderController`
  - `modules/model/service/ModelProviderAdminService`
- 写入规则：
  - `provider_code` 全局唯一。
  - `api_key_encrypted` 只允许写入和重置，不在详情接口返回明文。
  - 启停只影响数据库状态；首版是否影响运行时由后续动态配置能力决定。

#### 3.5 模型配置管理

- 复用表：
  - `model_config`
- 后端落点：
  - `modules/model/controller/admin/AdminModelConfigController`
  - `modules/model/service/ModelConfigAdminService`
- 写入规则：
  - `model_code` 全局唯一。
  - `provider_id` 必须指向存在的供应商。
  - 能力字段使用 0/1 保存，前端以开关展示。
  - 上下文窗口、最大输出 token、排序值需要做范围校验。

#### 3.6 提示词模板管理

- 新增表：
  - `prompt_template`
- 后端落点：
  - `modules/prompt/controller/admin/AdminPromptTemplateController`
  - `modules/prompt/service/PromptTemplateService`
  - `modules/prompt/entity/PromptTemplateDO`
- 主链路改造：
  - `DefaultChatPromptResolver` 改为优先读取启用模板。
  - 当 DB 查询失败或模板缺失时，回退当前会话级 / 请求级附加提示词直传能力，保证聊天主链路可用。
- 模板范围：
  - 首版以通用附加提示词模板为主，不再把 `quick / expert` 当作默认模板编码。
  - 暂不支持按模型、租户、场景分层模板。

#### 3.7 调用日志和用量只读

- 复用表：
  - `api_call_log`
  - `user_token_usage`
- 后端落点：
  - `modules/admin/controller/AdminApiCallLogController`
  - `modules/admin/controller/AdminUsageController`
  - 可复用 `modules/model`、`modules/billing` Mapper 或新增后台查询 Mapper。
- 查询规则：
  - 日志列表默认按 `created_at DESC`。
  - 时间范围建议限制最大跨度，避免后台一次扫全表。
  - 请求和响应 payload 只做摘要展示，不做重放。

#### 3.8 前端后台

- 路由建议：
  - `/admin/login`
  - `/admin`
  - `/admin/dashboard`
  - `/admin/model/providers`
  - `/admin/model/configs`
  - `/admin/prompts`
  - `/admin/users/admins`
  - `/admin/users/apps`
  - `/admin/logs/api-calls`
  - `/admin/usage`
- 页面结构：
  - 顶部/侧边导航 + 内容区。
  - 列表页统一使用 TDesign Table、Search Form、Pagination。
  - 新增/编辑统一使用 Dialog 或 Drawer。
- 状态管理：
  - 新增 `stores/admin-auth.ts` 保存后台 token 和管理员信息。
  - 后台请求封装统一附加后台 token。

### 4. 业务流程

#### 4.1 后台登录流程

1. 管理员访问 `/admin/login`。
2. 前端提交用户名和密码到 `POST /api/admin/auth/login`。
3. 后端校验 `admin_user` 状态和密码哈希。
4. 登录成功后更新 `last_login_at`，写入 `admin_login_log`。
5. 后端返回后台 token 和管理员基础信息。
6. 前端保存 token，跳转 `/admin/dashboard`。

#### 4.2 后台接口鉴权流程

1. 前端请求 `/api/admin/**` 时携带后台 token。
2. 后端后台鉴权拦截器解析 token。
3. 校验 token 有效且管理员状态正常。
4. 将管理员 ID 放入请求上下文。
5. Controller 调用 service 执行业务。

#### 4.3 模型供应商配置流程

1. 管理员在供应商页面新增或编辑供应商。
2. 后端校验 `provider_code` 唯一和字段合法性。
3. 如果提交 API Key，则加密后写入 `api_key_encrypted`。
4. 保存 `model_provider`。
5. 前端刷新列表。
6. 首版提示：DB 配置保存成功，不代表当前模型调用运行时已热刷新。

#### 4.4 提示词模板生效流程

1. 管理员编辑某个通用附加提示词模板。
2. 后端保存 `prompt_template`。
3. 聊天发送消息时，`ChatPromptResolver` 优先查询启用模板。
4. 如果模板缺失或查询异常，回退当前会话级 / 请求级附加提示词直传能力。
5. 模型请求继续按现有聊天主链路执行。

#### 4.5 日志和用量查询流程

1. 管理员在日志或用量页面输入筛选条件。
2. 前端请求后台分页接口。
3. 后端按条件查询 `api_call_log` 或聚合 `user_token_usage`。
4. 前端展示列表、聚合数值和详情。
5. 首版不提供日志删除和修改。

### 5. 接口 / 数据影响

#### 5.1 新增后端接口

- 后台登录：
  - `POST /api/admin/auth/login`
  - `POST /api/admin/auth/logout`
  - `GET /api/admin/auth/me`
- 管理用户：
  - `GET /api/admin/admin-users/page`
  - `GET /api/admin/admin-users/{adminUserId}`
  - `POST /api/admin/admin-users/create`
  - `POST /api/admin/admin-users/update`
  - `POST /api/admin/admin-users/update-status`
  - `POST /api/admin/admin-users/reset-password`
- 普通用户：
  - `GET /api/admin/app-users/page`
  - `GET /api/admin/app-users/{userId}`
  - `POST /api/admin/app-users/update-status`
- 模型供应商：
  - `GET /api/admin/model-providers/page`
  - `GET /api/admin/model-providers/{providerId}`
  - `POST /api/admin/model-providers/create`
  - `POST /api/admin/model-providers/update`
  - `POST /api/admin/model-providers/update-status`
- 模型配置：
  - `GET /api/admin/model-configs/page`
  - `GET /api/admin/model-configs/{modelId}`
  - `POST /api/admin/model-configs/create`
  - `POST /api/admin/model-configs/update`
  - `POST /api/admin/model-configs/update-status`
- 提示词模板：
  - `GET /api/admin/prompt-templates/page`
  - `GET /api/admin/prompt-templates/{templateId}`
  - `POST /api/admin/prompt-templates/create`
  - `POST /api/admin/prompt-templates/update`
  - `POST /api/admin/prompt-templates/update-status`
- 日志和用量：
  - `GET /api/admin/api-call-logs/page`
  - `GET /api/admin/api-call-logs/{logId}`
  - `GET /api/admin/usage/daily`
  - `GET /api/admin/usage/users/{userId}/summary`

#### 5.2 新增数据库表

- `admin_user`
- `admin_login_log`
- `prompt_template`

#### 5.3 复用数据库表

- `app_user`
- `model_provider`
- `model_config`
- `api_call_log`
- `user_token_usage`
- `user_balance`

#### 5.4 前端数据类型

- 新增后台通用分页查询类型。
- 新增 `AdminUser`、`AdminLoginRequest`、`AdminAuthInfo`。
- 新增 `AdminModelProvider`、`AdminModelConfig`。
- 新增 `PromptTemplate`。
- 新增 `ApiCallLogItem`、`UsageDailyItem`、`AppUserAdminItem`。

### 6. 边界与失败处理

- 登录失败：
  - 用户名不存在、密码错误、账号禁用均返回统一中文错误，不泄漏账号存在性。
  - 登录失败也记录 `admin_login_log`，便于排查。
- token 失效：
  - 后端返回未登录错误码。
  - 前端清理后台 token 并跳转 `/admin/login`。
- 模型供应商保存失败：
  - 唯一键冲突返回明确中文提示。
  - API Key 不回显，编辑时未传新 key 则保留旧 key。
- 提示词模板异常：
  - 模板为空或禁用时，聊天主链路回退默认模板。
  - 管理后台保存时禁止空模板内容。
- 日志查询压力：
  - 时间范围为空时默认查询近 7 天。
  - pageSize 最大 100。
- 普通用户禁用：
  - 只更新 `app_user.status`。
  - 在登录鉴权真正接入前，不把它描述成已完整阻断用户访问。

### 7. 验收清单

- [ ] 可通过 `/admin/login` 使用管理员账号登录后台。
- [ ] 未登录访问 `/admin/**` 会跳转登录页。
- [ ] 未携带后台 token 请求 `/api/admin/**` 会返回未登录错误。
- [ ] 管理员可新增、编辑、启停管理用户。
- [ ] 管理员可分页查询普通用户、查看用户详情、调整普通用户状态。
- [ ] 管理员可维护模型供应商，API Key 不明文回显。
- [ ] 管理员可维护模型配置，用户侧 `GET /api/model/list` 仍只返回启用模型和能力字段。
- [ ] 管理员可维护通用附加提示词模板。
- [ ] 提示词模板缺失或禁用时，聊天发送仍可回退会话级 / 请求级附加提示词直传能力。
- [ ] 管理员可分页查询模型调用日志。
- [ ] 管理员可查看按日期、用户、模型维度的 token 用量统计。
- [ ] 后台页面使用 TDesign 风格，和现有前端技术栈一致。

### 8. 非功能需求

- 权限：
  - `/api/admin/**` 统一鉴权。
  - 后台 token 不复用普通用户 `X-User-Id`。
- 安全：
  - 管理员密码哈希保存。
  - 供应商 API Key 不明文返回。
  - 登录失败不暴露账号是否存在。
- 性能：
  - 列表接口全部分页。
  - 日志和用量查询必须带默认时间范围或最大范围限制。
- 兼容性：
  - 不破坏现有 `/chat` 页面和用户侧接口。
  - 用户侧模型列表继续使用 `GET /api/model/list`。
- 可回退：
  - 提示词模板 DB 异常时回退默认硬编码模板。
  - 模型供应商 DB 配置首版不影响运行时可用性判断，避免误配置导致聊天主链路不可用。

### 9. 测试与验收用例

- 后台登录：
  - 正确账号密码登录成功。
  - 错误密码登录失败。
  - 禁用管理员登录失败。
  - 登录成功写入登录日志。
- 后台鉴权：
  - 无 token 访问后台接口失败。
  - 无效 token 访问后台接口失败。
  - 有效 token 访问后台接口成功。
- 管理用户：
  - 新增管理员成功。
  - 重复用户名新增失败。
  - 启停管理员成功。
  - 重置密码后旧密码不可用。
- 普通用户：
  - 用户列表分页查询成功。
  - 用户详情返回余额和近期用量摘要。
  - 调整用户状态成功。
- 模型供应商：
  - 新增供应商成功。
  - 重复 `provider_code` 失败。
  - 编辑时不传 API Key 保留旧值。
  - 详情接口不返回明文 API Key。
- 模型配置：
  - 新增模型成功。
  - 重复 `model_code` 失败。
  - 启停模型后用户侧模型列表只返回启用模型。
- 提示词模板：
  - 编辑 quick 模板后快速模式优先使用 DB 模板。
  - 禁用模板后回退默认模板。
- 日志和用量：
  - 日志分页查询成功。
  - 按时间范围筛选成功。
  - 用量聚合结果与 `user_token_usage` 明细一致。

## 变更记录

| 日期 | 变更内容 | 影响文档 |
| --- | --- | --- |
| 2026-05-31 | 初始化管理后台设计文档，确认首版范围为运营配置后台、管理用户和普通用户管理 | `管理后台-task.md` |
