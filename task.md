# GPT Plus 项目总任务跟踪

最后更新：2026-05-09

## 项目目标

- 搭建一个多模型聚合的 AI 聊天网站
- 技术栈：`Vue + Java + Spring Boot + MySQL`
- 当前后端项目目录：`gpt-plus-core`

## 任务拆分说明

当前项目任务拆分为 3 份文件维护：

- 总任务清单： [task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/task.md)
- 后端任务清单： [docs/backend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/task.md)
- 前端任务清单： [docs/frontend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/task.md)

原则：

- 总任务清单只维护项目总览、阶段、跨端阻塞项和整体下一步
- 后端详细任务只维护在 `docs/backend/task.md`
- 前端详细任务只维护在 `docs/frontend/task.md`

## 当前阶段

当前处于：`后端真实模型流式链路已验证，前端尚未初始化，项目进入前端开工与后端基础工程补齐阶段`

## 当前状态总览

### 后端

- Maven 工程已初始化
- MySQL 数据源已配置
- 核心业务表已初始化
- 基础聊天会话/消息链路已接通
- `ChatModelClient` 抽象层已接入
- DeepSeek 适配器已落地
- 真实 DeepSeek 流式调用已验证
- `api_call_log` / `user_token_usage` 已接通落库

详细见：

- [docs/backend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/task.md)

### 前端

- 前端技术栈已确定
- 前端任务清单已建立
- 前端工程尚未初始化

详细见：

- [docs/frontend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/task.md)

## 当前阻塞项

- [ ] 前端工程目录尚未初始化（添加时间：2026-05-08）
- [ ] Maven 依赖下载偶发超时（添加时间：2026-05-08）

## 已完成事项

- [x] 创建独立后端任务清单（添加时间：2026-05-08，完成时间：2026-05-08）
- [x] 创建独立前端任务清单（添加时间：2026-05-08，完成时间：2026-05-08）
- [x] 将项目任务整理为“总任务 / 后端任务 / 前端任务”三份清单（添加时间：2026-05-08，完成时间：2026-05-08）
- [x] 关联 GitHub 远程仓库、切换主分支为 `main` 并完成首次提交推送（添加时间：2026-05-08，完成时间：2026-05-08）
- [x] 将 API 测试文档拆分为规则、标准用例和执行结果三层结构（添加时间：2026-05-09，完成时间：2026-05-09）

## 建议的下一步

下一步优先做这 4 件事：

1. 初始化前端项目目录和 Vue 工程
2. 开始聊天首页静态布局开发
3. 后端补参数校验、错误码和健康检查
4. 补模型初始化数据方案，避免新环境还要手动补 `model_provider` / `model_config`

## 维护规则

本文件的维护要求同时受项目规则约束，见：

- [project-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/project-rules.md)

- 每次完成一个明确任务，就把对应条目标记为 `[x]`
- 新增任务时，记录添加时间，例如：`（添加时间：2026-05-08）`
- 任务完成时，记录完成时间，例如：`（添加时间：2026-05-08，完成时间：2026-05-10）`
- 已有未带时间的历史任务保持不变，不强制补时间
- 总任务只维护总览，详细任务分别维护在后端和前端清单中
