# GPT Plus 项目目录结构说明

最后更新：2026-05-19

## 目的

本文档用于说明当前仓库中前端、后端和项目文档目录的职责边界，方便后续新增代码、文档、配置和测试材料时放到稳定位置。

## 总体结构

```text
ai-code-gpt-plus/
├── task.md
├── docs/
│   ├── backend/
│   ├── db/
│   ├── frontend/
│   └── rules/
├── gpt-plus-core/
└── gpt-plus-web/
```

| 目录/文件 | 作用 |
| --- | --- |
| `task.md` | 项目总任务清单，只维护整体阶段、跨端状态、阻塞项和建议下一步。 |
| `docs/` | 项目长期文档目录，保存规则、设计、启动、测试、数据库和目录说明。 |
| `gpt-plus-core/` | 后端 Spring Boot 服务目录，负责接口、聊天业务、模型适配、持久化和配置。 |
| `gpt-plus-web/` | 前端 Vue 3 应用目录，负责聊天页面、路由、状态管理、接口请求和交互展示。 |

## 后端目录：`gpt-plus-core`

```text
gpt-plus-core/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/example/aichat/
│   │   └── resources/
│   └── test/
```

| 目录/文件 | 作用 |
| --- | --- |
| `pom.xml` | Maven 工程配置，维护 Spring Boot、MyBatis Plus、MySQL、Lombok、测试等依赖。 |
| `src/main/java/com/example/aichat/GptPlusCoreApplication.java` | 后端应用启动入口。 |
| `src/main/java/com/example/aichat/common/` | 通用基础能力目录，放统一响应、分页响应、异常、错误码、基础 DO、健康检查等。 |
| `src/main/java/com/example/aichat/infrastructure/` | 基础设施适配层，放外部模型客户端、模型注册表、DeepSeek 和 mock 适配等。 |
| `src/main/java/com/example/aichat/modules/` | 业务模块目录，按业务域拆分聊天、模型、用户、计费等模块。 |
| `src/main/resources/application.yml` | 后端默认配置文件，维护服务端口、数据库、MyBatis、模型配置等。 |
| `src/main/resources/application-local.example.yml` | 本地私密配置示例模板，可提交到 Git。 |
| `src/main/resources/application-local.yml` | 本地真实私密配置文件，不应提交到 Git。 |
| `src/main/resources/mapper/` | MyBatis XML 映射文件目录，按业务模块拆分。 |
| `src/test/` | 后端测试代码目录，后续补单元测试、集成测试时放这里。 |

### 后端 Java 分层

```text
com/example/aichat/
├── common/
├── infrastructure/
└── modules/
```

| 目录 | 作用 |
| --- | --- |
| `common/controller/` | 通用控制器，例如健康检查接口。 |
| `common/dto/` | 通用响应对象，例如 `CommonResponse`、`PageResponse`。 |
| `common/enums/` | 通用枚举，例如角色、消息状态、聊天模式、错误码。 |
| `common/exception/` | 业务异常定义。 |
| `common/handler/` | 全局异常处理等横切处理器。 |
| `common/model/` | 基础数据模型，例如通用 DO 基类。 |
| `infrastructure/ai/` | 模型调用抽象层，定义统一请求、响应、流式 chunk、客户端接口和客户端注册表。 |
| `infrastructure/ai/deepseek/` | DeepSeek 真实模型调用适配。 |
| `infrastructure/ai/mock/` | 本地开发兜底的 mock 模型适配。 |
| `modules/chat/` | 聊天核心业务，包含会话、消息、发送消息、SSE 流式输出和提示词解析。 |
| `modules/model/` | 模型配置业务，包含模型供应商、模型列表、模型能力字段和调用日志。 |
| `modules/user/` | 用户基础数据模块。 |
| `modules/billing/` | 计费和用量预留模块，包含余额和 token 使用记录。 |

### 后端业务模块约定

业务模块优先按以下结构组织：

```text
modules/{module-name}/
├── controller/
├── dto/
├── entity/
├── mapper/
├── service/
│   └── impl/
└── vo/
```

| 目录 | 作用 |
| --- | --- |
| `controller/` | HTTP 接口入口，负责参数接收、基础校验和调用 service。 |
| `dto/` | 请求参数对象，主要承载前端传入的数据。 |
| `entity/` | 数据库表映射对象，当前项目后缀通常为 `DO`。 |
| `mapper/` | MyBatis Mapper Java 接口。 |
| `service/` | 业务服务接口或领域服务抽象。 |
| `service/impl/` | 业务服务实现，放主要业务编排、状态更新和持久化调用。 |
| `vo/` | 接口响应对象，主要承载返回给前端的数据结构。 |

### 后端资源目录约定

```text
src/main/resources/
├── application.yml
├── application-local.example.yml
├── application-local.yml
└── mapper/
    ├── billing/
    ├── chat/
    ├── model/
    └── user/
```

| 目录/文件 | 作用 |
| --- | --- |
| `mapper/chat/` | 聊天会话、聊天消息相关 SQL 映射。 |
| `mapper/model/` | 模型供应商、模型配置、模型调用日志相关 SQL 映射。 |
| `mapper/user/` | 用户相关 SQL 映射。 |
| `mapper/billing/` | 余额、token 用量相关 SQL 映射。 |

## 前端目录：`gpt-plus-web`

```text
gpt-plus-web/
├── package.json
├── vite.config.ts
├── index.html
├── tsconfig*.json
├── eslint.config.js
├── .prettierrc.json
├── .env.example
└── src/
```

| 目录/文件 | 作用 |
| --- | --- |
| `package.json` | 前端依赖和脚本入口，维护 `dev`、`build`、`lint`、`format`、`preview` 等命令。 |
| `vite.config.ts` | Vite 配置，负责 Vue 插件、本地开发代理、构建等配置。 |
| `index.html` | 前端 HTML 入口。 |
| `tsconfig.json` / `tsconfig.app.json` | TypeScript 编译配置。 |
| `eslint.config.js` | ESLint 配置。 |
| `.prettierrc.json` | Prettier 格式化配置。 |
| `.env.example` | 前端环境变量示例，例如接口地址。 |
| `src/` | 前端源码目录。 |

### 前端源码目录

```text
gpt-plus-web/src/
├── App.vue
├── main.ts
├── vite-env.d.ts
├── config/
├── lib/
├── router/
├── stores/
├── styles/
├── types/
└── views/
```

| 目录/文件 | 作用 |
| --- | --- |
| `App.vue` | Vue 根组件，承载路由出口和全局页面外壳。 |
| `main.ts` | 前端应用启动入口，初始化 Vue、Pinia、Router、TDesign 样式等。 |
| `vite-env.d.ts` | Vite 环境类型声明。 |
| `config/` | 前端运行配置和模型本地 fallback 配置。 |
| `lib/` | 通用工具能力，当前包含 HTTP 请求封装和错误处理。 |
| `router/` | Vue Router 路由配置，维护页面路径和组件映射。 |
| `stores/` | Pinia 状态管理目录，当前核心为聊天状态流和 SSE 消费逻辑。 |
| `styles/` | 全局样式目录。 |
| `types/` | TypeScript 类型定义目录，维护聊天、接口等共享类型。 |
| `views/` | 页面级 Vue 组件目录，当前核心页面为聊天页。 |

## 文档目录：`docs`

```text
docs/
├── project-directory-structure.md
├── backend/
├── db/
├── frontend/
└── rules/
```

| 目录/文件 | 作用 |
| --- | --- |
| `project-directory-structure.md` | 当前文档，说明仓库目录职责和新增文件放置规则。 |
| `backend/` | 后端任务、启动、接口 DTO、实体 Mapper、API 用例和执行结果文档。 |
| `db/` | 数据库建表 SQL 和表关系说明。 |
| `frontend/` | 前端任务、技术栈、本地启动流程文档。 |
| `rules/` | 项目执行规则和 API 测试规则。 |

### `docs/backend`

| 文件/目录 | 作用 |
| --- | --- |
| `task.md` | 后端详细任务清单。 |
| `backend-local-setup.md` | 后端本地环境、配置、启动和验证说明。 |
| `chat-api-dto-design.md` | 聊天接口 DTO/VO 设计说明。 |
| `chat-response-resume-design.md` | 聊天响应中断后继续技术方案，约定状态流、接口语义和前后端实现顺序。 |
| `chat-response-resume-flow.md` | 聊天响应中断后继续流程图文档，用 Mermaid 展示主链路、状态流转和前后端交互。 |
| `springboot-entity-mapper-design.md` | Spring Boot 实体和 Mapper 设计说明。 |
| `api-test-cases.md` | 稳定 API 测试用例。 |
| `api-test-results/` | 每次真实联调或回归的执行结果记录。 |

### `docs/frontend`

| 文件 | 作用 |
| --- | --- |
| `task.md` | 前端详细任务清单。 |
| `frontend-tech-stack.md` | 前端技术栈约定。 |
| `frontend-local-setup.md` | 前端本地安装、启动、构建、lint 和 format 说明。 |

### `docs/db`

| 文件 | 作用 |
| --- | --- |
| `mysql-schema.sql` | 当前项目数据库初始化 SQL。 |
| `table-relations.md` | 数据表关系说明。当前项目不在数据库层创建外键，关系通过文档维护。 |

### `docs/rules`

| 文件 | 作用 |
| --- | --- |
| `project-rules.md` | 项目推进规则，例如任务清单维护、文档同步、注释要求等。 |
| `api-test-rules.md` | API 测试文档和执行结果维护规则。 |

## 新增目录和文件放置规则

- 新增后端业务代码：优先放到 `gpt-plus-core/src/main/java/com/example/aichat/modules/{业务模块}/`。
- 新增外部服务适配：优先放到 `gpt-plus-core/src/main/java/com/example/aichat/infrastructure/`。
- 新增通用响应、异常、枚举、基础模型：优先放到 `gpt-plus-core/src/main/java/com/example/aichat/common/`。
- 新增 MyBatis XML：放到 `gpt-plus-core/src/main/resources/mapper/{业务模块}/`。
- 新增前端页面：放到 `gpt-plus-web/src/views/`，并在 `gpt-plus-web/src/router/` 补路由。
- 新增前端全局状态：放到 `gpt-plus-web/src/stores/`。
- 新增前端接口类型：放到 `gpt-plus-web/src/types/`。
- 新增前端通用请求、错误处理、纯工具函数：放到 `gpt-plus-web/src/lib/`。
- 新增项目规则：放到 `docs/rules/`，必要时同步链接到 `docs/rules/project-rules.md`。
- 新增后端设计、启动、接口、测试文档：放到 `docs/backend/`。
- 新增前端设计、启动、交互、技术栈文档：放到 `docs/frontend/`。
- 新增数据库结构或表关系说明：放到 `docs/db/`。
- 新增跨端或全局项目说明：优先放到 `docs/` 根目录。

## 不建议的放置方式

- 不把后端业务代码放到 `common/`，除非它确实是跨模块通用能力。
- 不把外部模型、第三方接口调用直接写散在 controller 中，应优先通过 `infrastructure/` 或 service 层封装。
- 不把前端请求逻辑散落在页面组件中，通用请求和错误处理应沉到 `lib/` 或 `stores/`。
- 不把一次性联调结果写进 `api-test-cases.md`，真实执行记录应放到 `docs/backend/api-test-results/`。
- 不把本地真实密钥提交到 Git，后端用 `application-local.yml`，前端用本地 `.env` 文件。
