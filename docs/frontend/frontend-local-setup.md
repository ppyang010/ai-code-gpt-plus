# 前端本地启动说明

最后更新：2026-05-18

## 项目目录

- 前端项目目录：`gpt-plus-web`

## 当前本地运行依赖

### Node.js

当前本地已验证的版本：

- `Node.js v24.11.1`

### npm

当前本地已验证的版本：

- `npm 11.6.4`

## 安装依赖

首次进入前端目录后，执行：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm install --registry=https://registry.npmjs.org
```

说明：

- 当前机器默认 npm registry 可能不是官方源
- 如果直接 `npm install` 出现 `registry.npm.taobao.org` 解析失败，可显式指定官方源
- 安装完成后会生成 `package-lock.json`，当前项目建议保留并提交

## 环境变量

当前前端已提供示例文件：

- [gpt-plus-web/.env.example](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/.env.example)

当前支持的变量：

```bash
VITE_API_BASE_URL=/api
VITE_USER_ID=1
```

说明：

- `VITE_API_BASE_URL` 默认可以直接使用 `/api`
- `VITE_USER_ID` 当前用于兼容后端临时 `X-User-Id` 方案
- 如果需要本地自定义，可自行创建未提交的 `.env.local`

## 本地启动

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm run dev
```

默认启动后：

- 本地开发地址通常为：`http://127.0.0.1:5173`
- 若端口被占用，Vite 会提示新的端口
- 当前 `vite.config.ts` 已内置 `/api -> http://127.0.0.1:8080` 代理，便于本地联调

## 生产构建验证

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm run build
```

当前已验证结果：

- `vue-tsc -b` 通过
- `vite build` 通过

## 代码质量校验

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web
npm run lint
npm run format:check
```

如果需要自动格式化，执行：

```bash
npm run format
```

当前已接入：

- `ESLint`
- `Prettier`
- `eslint-plugin-vue`
- `typescript-eslint`

## 当前前端骨架内容

当前前端已具备：

- `Vue 3 + Vite + TypeScript`
- `Pinia`
- `Vue Router`
- `TDesign Vue Next`
- `@tdesign-vue-next/chat`
- 聊天首页静态布局
- API 环境变量读取
- `/api` 本地代理
- 基础请求封装
- 会话列表 / 创建会话 / 消息列表接口代码路径
- 发送消息接口和 SSE 增量消费代码路径
- 本地固定模型选项：`deepseek-v4-flash` / `deepseek-v4-pro`
- 创建会话和发送消息都会带上正确 `modelId`
- lint / format 脚本和配置

当前入口文件：

- [package.json](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/package.json)
- [vite.config.ts](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/vite.config.ts)
- [src/main.ts](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/src/main.ts)
- [src/views/ChatView.vue](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-web/src/views/ChatView.vue)

## 当前已知情况

- 当前前端已完成会话列表、创建会话、消息列表、发送消息和 SSE 的本地真实联调验证
- `npm run build` 会提示 chunk size warning，但不影响当前工程可运行和可构建

## 给后续对话的结论

如果后续继续在这个项目上开发前端，默认按下面前提理解：

1. 前端项目目录是 `gpt-plus-web`
2. 依赖安装优先使用官方 npm registry
3. 本地启动命令是 `npm run dev`
4. 构建校验命令是 `npm run build`
5. 代码质量校验命令是 `npm run lint` 和 `npm run format:check`
6. 默认通过 `/api` 代理访问本地后端
