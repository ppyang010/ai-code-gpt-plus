# 后端本地环境与启动说明

最后更新：2026-05-15

## 项目目录

- 后端项目目录：`gpt-plus-core`

## 当前本地运行依赖

### JDK

当前项目要求使用：

- `JDK 25`

本机已配置的 JDK 25 路径为：

- `/Users/ccy/java/jdk-25.0.2+10/Contents/Home`

### JDK 版本管理

当前项目使用：

- `jenv`

已完成的本地项目级配置：

- 已将 JDK 25 注册到 `jenv`
- 已在项目根目录写入 [`.java-version`](/Users/ccy/CcyProjects/ai-code-gpt-plus/.java-version)
- 当前项目本地版本为：`25.0.2`

## 注意事项

当前机器的全局 shell 环境仍可能默认落在 JDK 8。

因此在本项目中，推荐优先使用：

```bash
jenv exec java -version
jenv exec mvn -version
```

而不要直接依赖裸命令的：

```bash
java -version
mvn -version
```

## 数据库配置

当前本地数据库连接信息如下：

- Host：`127.0.0.1`
- Port：`3306`
- Database：`mysql`
- Username：`root`
- Password：`123456`

项目中的 Spring Boot 数据源配置已写入：

- [application.yml](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/resources/application.yml)

## 数据库初始化状态

已完成：

- 已执行 [mysql-schema.sql](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/db/mysql-schema.sql)
- 已初始化 8 张核心业务表

## 模型接入状态

当前后端已接入以下模型层结构：

- `ChatModelClient`
- `ChatModelClientRegistry`
- `DeepSeekChatModelClient`
- `MockChatModelClient`

当前行为如下：

- 如果所选模型编码以 `deepseek` 开头，并且已配置 `DEEPSEEK_API_KEY`，则优先走 DeepSeek 客户端
- DeepSeek 客户端当前已经改为真实 SSE 流式读取，而不是一次性非流式回包再转发
- 如果没有配置 `DEEPSEEK_API_KEY`，则自动回退到 mock 客户端，保证本地开发链路可用

## DeepSeek 配置

当前项目支持以下环境变量：

```bash
export DEEPSEEK_BASE_URL=https://api.deepseek.com
export DEEPSEEK_API_KEY=your_api_key
export DEEPSEEK_DEFAULT_MODEL=deepseek-v4-flash
```

对应配置位置：

- [application.yml](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/resources/application.yml)

如果没有配置 `DEEPSEEK_API_KEY`：

- 编译不受影响
- 启动不受影响
- 模型调用默认回退到 mock 客户端

如果已经配置 `DEEPSEEK_API_KEY`：

- 可以直接验证真实 DeepSeek 流式响应
- 发送消息成功后会同步落库 `chat_message`、`api_call_log`、`user_token_usage`

## 当前本地模型初始化约定

当前本地数据库保留两个启用模型：

- `id = 1`：`deepseek-v4-flash`
- `id = 2`：`deepseek-v4-pro`

同时 `model_config` 已显式增加以下能力字段：

- `support_thinking`
- `support_json_output`

当前默认模型固定为：

- `deepseek-v4-flash`

## 本地密钥保存方式

当前项目已支持把本地私密配置独立保存在：

- [application-local.example.yml](/Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core/src/main/resources/application-local.example.yml)
- `gpt-plus-core/src/main/resources/application-local.yml`

其中：

- `application-local.example.yml` 可以提交到 Git，作为示例模板
- `application-local.yml` 用于保存本地真实密钥，已经加入 `.gitignore`

这样可以达到：

- 密钥保存在项目内，方便本地启动
- 密钥不会进入 Git 提交
- 其他人可以用 example 文件复制自己的本地配置

## 使用本地配置启动

如果需要显式启用本地配置，建议使用：

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn spring-boot:run -Dspring-boot.run.profiles=local
```

如果后续你希望统一走 `local` 配置文件，也可以在 IDEA 或启动脚本里固定加上：

- `spring.profiles.active=local`

## 推荐的本地验证命令

### 1. 验证当前项目 JDK

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus
jenv exec java -version
```

期望看到：

- `openjdk version "25.0.2"`

### 2. 验证 Maven 使用的 JDK

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn -version
```

期望看到：

- `Java version: 25.0.2`

### 3. 编译项目

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn -q -DskipTests compile
```

### 4. 启动项目

```bash
cd /Users/ccy/CcyProjects/ai-code-gpt-plus/gpt-plus-core
jenv exec mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 5. 验证真实 DeepSeek 流式输出

```bash
curl -N -X POST "http://127.0.0.1:8080/api/chat/message/send" \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{
    "sessionId": 1,
    "modelId": 1,
    "content": "请用一句话确认你是 DeepSeek 真流式响应。"
  }'
```

期望看到：

- 先收到 `message_start`
- 持续收到多个 `message_delta`
- 最后收到 `message_end`

## 当前已知问题

- 当前机器全局 `java` 可能仍指向 JDK 8
- 因此如果不通过 `jenv exec` 运行，可能出现版本不匹配
- 之前出现过 Maven 依赖下载超时问题，网络不稳定时可能影响首次构建

## 给后续对话的结论

如果后续继续在这个项目上开发，默认按下面前提理解：

1. 后端项目目录是 `gpt-plus-core`
2. 本地数据库已经初始化完成
3. 项目 JDK 应使用 `jenv` 管理的 `25.0.2`
4. 编译和启动优先使用 `jenv exec` 前缀执行
