# 前端技术栈约定

最后更新：2026-05-04

## 当前前端技术栈

当前项目前端统一采用以下技术栈：

- `Vue 3`
- `Vite`
- `TypeScript`
- `Pinia`
- `Vue Router`
- `TDesign Vue Next`

## 聊天相关配套能力

聊天页面相关能力默认采用：

- 流式通信：`SSE`
- Markdown 渲染：`markdown-it`
- 代码高亮：`highlight.js`
- AI 聊天 UI：`@tdesign-vue-next/chat`
- 代码质量：`ESLint + Prettier`

## 各技术的作用说明

### Vue 3

- 前端主框架
- 用来编写页面、组件和交互逻辑
- 聊天页面、会话列表、消息气泡、输入框等都由它实现

### Vite

- 前端构建工具
- 负责本地开发启动、热更新和生产打包
- 可以理解为前端项目的运行器和打包器

### TypeScript

- JavaScript 的类型增强版本
- 用来约束接口字段、组件参数和数据结构
- 能减少低级错误，特别适合前后端接口较多的项目

### Pinia

- Vue 的状态管理工具
- 负责存放全局共享数据
- 例如：登录用户、当前会话、模型列表、当前选中的模型

### Vue Router

- 前端路由管理工具
- 负责不同页面地址和页面组件之间的映射
- 例如：`/login`、`/chat`、`/settings`

### TDesign Vue Next

- Vue 3 UI 组件库
- 提供按钮、表单、弹窗、下拉框、表格等基础组件
- 能加快后台页、登录页、设置页等界面的开发速度

### SSE

- `Server-Sent Events`
- 用于服务端向前端持续推送消息
- 在 AI 聊天场景里主要用来实现流式输出

### markdown-it

- Markdown 解析器
- 用来把 AI 返回的 Markdown 文本渲染成页面内容
- 可支持标题、列表、引用、表格、代码块等结构

### highlight.js

- 代码高亮库
- 用来高亮 AI 返回的代码块
- 适合展示 Java、Vue、SQL、Shell 等代码内容

## 在当前项目中的职责对应

- `Vue 3`：负责聊天界面和组件开发
- `Vite`：负责前端本地运行和打包
- `TypeScript`：负责接口类型和组件类型约束
- `Pinia`：负责全局状态管理
- `Vue Router`：负责页面切换
- `TDesign Vue Next`：负责基础 UI 组件
- `@tdesign-vue-next/chat`：负责对话列表、聊天内容、操作栏和输入框等 AI Chat 组件
- `SSE`：负责 AI 流式回复
- `markdown-it`：负责渲染 AI 的 Markdown 回答
- `highlight.js`：负责高亮 AI 回答中的代码块
- `ESLint + Prettier`：负责前端代码检查和格式统一

## 说明

- 当前仓库还没有初始化前端工程。
- 后续创建前端项目时，应默认基于这套技术栈落地。
- 如果后续技术栈调整，需要同步更新本文件和 `task.md`。
