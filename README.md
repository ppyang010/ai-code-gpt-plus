# GPT Plus

多模型聚合 AI 聊天项目，当前仓库包含：

- `gpt-plus-core`：Spring Boot 4 后端
- `gpt-plus-web`：Vue 3 + Vite 前端
- `scripts/`：本地启动脚本
- `docs/`：项目设计、规则、启动说明和任务文档

## 1. 目录概览

```text
ai-code-gpt-plus/
├── README.md
├── task.md
├── docs/
├── scripts/
├── gpt-plus-core/
└── gpt-plus-web/
```

更多目录说明见：

- [docs/project-directory-structure.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/project-directory-structure.md)

## 2. 本地运行前提

### 后端

- JDK：`25`
- Maven：通过 `jenv` 管理并执行
- MySQL：`127.0.0.1:3306/mysql`

后端详细说明见：

- [docs/backend/backend-local-setup.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/backend-local-setup.md)

### 前端

- Node.js：`v24.11.1`
- npm：`11.6.4`

前端详细说明见：

- [docs/frontend/frontend-local-setup.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/frontend-local-setup.md)

## 3. 启动脚本说明

仓库根目录提供了 3 个常用脚本：

### `./scripts/start-backend.sh`

作用：

- 启动后端服务
- 默认使用 `local` profile

等价命令：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn spring-boot:run -Dspring-boot.run.profiles=local
```

用法：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-backend.sh
```

如果需要切换 profile：

```bash
SPRING_PROFILE=dev ./scripts/start-backend.sh
```

### `./scripts/start-frontend.sh`

作用：

- 启动前端开发服务
- 默认监听 `127.0.0.1:5173`

等价命令：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm run dev -- --host 127.0.0.1 --port 5173
```

用法：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-frontend.sh
```

如果需要自定义监听地址：

```bash
VITE_HOST=0.0.0.0 VITE_PORT=5174 ./scripts/start-frontend.sh
```

### `./scripts/start-services.sh`

作用：

- 同时启动前端和后端
- 任一子进程退出时，自动结束另一个
- 适合本地联调

用法：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-services.sh
```

## 4. 推荐启动方式

### 只调后端

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-backend.sh
```

### 只调前端

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-frontend.sh
```

### 前后端联调

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
./scripts/start-services.sh
```

启动后默认访问地址：

- 前端：`http://127.0.0.1:5173`
- 后端：`http://127.0.0.1:8080`
- 健康检查：`http://127.0.0.1:8080/api/health`

## 5. 停止服务

如果你是前台直接运行脚本：

- 在当前终端按 `Ctrl + C` 即可停止

如果是通过 `start-services.sh` 启动：

- 按一次 `Ctrl + C`，前后端会一起停止

## 6. 常用校验命令

### 前端

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm run lint
npm run build
```

### 后端

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn -q -DskipTests compile
```

## 7. 相关文档

- 项目总任务： [task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/task.md)
- 后端任务： [docs/backend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/task.md)
- 前端任务： [docs/frontend/task.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/task.md)
- 后端启动说明： [docs/backend/backend-local-setup.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/backend/backend-local-setup.md)
- 前端启动说明： [docs/frontend/frontend-local-setup.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/frontend/frontend-local-setup.md)
- 项目规则： [docs/rules/project-rules.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/rules/project-rules.md)
