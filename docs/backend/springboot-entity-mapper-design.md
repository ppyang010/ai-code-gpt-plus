# Spring Boot Entity / Mapper 设计

## 设计前提

- 技术栈默认采用 `Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Lombok`
- 数据库表结构基于 [mysql-schema.sql](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/db/mysql-schema.sql)
- 当前数据库 **不创建外键约束**
- 关联关系由应用层校验，说明文档见 [table-relations.md](/Users/ccy/CcyProjects/ai-code-gpt-plus/docs/db/table-relations.md)
- 这里采用 `DO(Data Object)` 命名，专门表示数据库映射对象
- `quick / expert` 模式建议通过“模式绑定提示词模板”实现

## 推荐依赖

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-boot-starter</artifactId>
        <version>3.5.7</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

## 推荐包结构

假设基础包名为 `com.example.aichat`，推荐结构如下：

```text
com.example.aichat
├── common
│   ├── config
│   ├── constant
│   ├── enums
│   ├── exception
│   └── model
│       └── BaseDO.java
├── modules
│   ├── user
│   │   ├── controller
│   │   ├── service
│   │   ├── mapper
│   │   ├── entity
│   │   └── dto
│   ├── chat
│   │   ├── controller
│   │   ├── service
│   │   ├── mapper
│   │   ├── entity
│   │   └── dto
│   ├── model
│   │   ├── service
│   │   ├── mapper
│   │   └── entity
│   └── billing
│       ├── service
│       ├── mapper
│       └── entity
└── infrastructure
    ├── ai
    ├── persistence
    └── security
```

如果想更简单，也可以把所有实体统一放在：

```text
com.example.aichat.persistence
├── entity
├── mapper
└── xml
```

如果你准备把提示词模板也做成独立模块，推荐补充：

```text
com.example.aichat
└── modules
    └── prompt
        ├── service
        ├── mapper
        └── entity
```

## 命名约定

- 实体类：`AppUserDO`
- Mapper 接口：`AppUserMapper`
- Mapper XML：`AppUserMapper.xml`
- Service：`AppUserService`
- 表名与字段名：保持数据库中的下划线命名
- Java 字段名：使用驼峰命名

## 公共基类设计

建议把公共字段抽出来，避免每张表重复写。

### BaseDO

适用于包含 `id / created_at / updated_at` 的表。

```java
package com.example.aichat.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BaseDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

注意：

- `api_call_log` 没有 `updated_at`，它可以单独写，不继承 `BaseDO`
- 如果你后面想接入自动填充，可再加 `MetaObjectHandler`

## 模式与提示词模板设计建议

针对 `quick / expert`，建议不要在代码里写很多 `if/else` 直接拼 prompt，而是抽成“模式 -> 默认提示词模板”的配置。

推荐解析顺序：

1. 根据 `modeCode` 找到模式默认提示词
2. 追加 `chat_session.system_prompt`
3. 追加本次请求的 `systemPrompt`

建议最终由独立组件统一处理，例如：

```java
public interface ChatPromptResolver {

    String resolveSystemPrompt(String modeCode, String sessionPrompt, String requestPrompt);
}
```

这样做的好处：

- 模式切换逻辑更清晰
- 后续更容易改成数据库模板
- 不会把 prompt 拼装散落在 Controller / Service / Provider Adapter 中

## 各表 Entity 设计

下面以 `modules` 分层为例。

### 1. AppUserDO

对应表：`app_user`

```java
package com.example.aichat.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app_user")
public class AppUserDO extends BaseDO {

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("nickname")
    private String nickname;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("email")
    private String email;

    @TableField("mobile")
    private String mobile;

    @TableField("status")
    private Integer status;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}
```

### 2. ModelProviderDO

对应表：`model_provider`

```java
package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_provider")
public class ModelProviderDO extends BaseDO {

    @TableField("provider_code")
    private String providerCode;

    @TableField("provider_name")
    private String providerName;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_encrypted")
    private String apiKeyEncrypted;

    @TableField("default_headers")
    private String defaultHeaders;

    @TableField("status")
    private Integer status;

    @TableField("sort_no")
    private Integer sortNo;

    @TableField("remark")
    private String remark;
}
```

说明：

- `JSON` 字段第一版直接用 `String` 接最稳
- 后续如果你想做自动序列化，可以再接 `JacksonTypeHandler`

### 3. ModelConfigDO

对应表：`model_config`

```java
package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_config")
public class ModelConfigDO extends BaseDO {

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_code")
    private String modelCode;

    @TableField("model_name")
    private String modelName;

    @TableField("model_type")
    private String modelType;

    @TableField("support_stream")
    private Integer supportStream;

    @TableField("support_vision")
    private Integer supportVision;

    @TableField("support_file")
    private Integer supportFile;

    @TableField("context_window")
    private Integer contextWindow;

    @TableField("max_output_tokens")
    private Integer maxOutputTokens;

    @TableField("temperature_default")
    private BigDecimal temperatureDefault;

    @TableField("top_p_default")
    private BigDecimal topPDefault;

    @TableField("extra_config")
    private String extraConfig;

    @TableField("status")
    private Integer status;

    @TableField("sort_no")
    private Integer sortNo;
}
```

### 4. ChatSessionDO

对应表：`chat_session`

```java
package com.example.aichat.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_session")
public class ChatSessionDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("title")
    private String title;

    @TableField("mode_code")
    private String modeCode;

    @TableField("default_model_id")
    private Long defaultModelId;

    @TableField("system_prompt")
    private String systemPrompt;

    @TableField("last_message_at")
    private LocalDateTime lastMessageAt;

    @TableField("status")
    private Integer status;
}
```

### 5. ChatMessageDO

对应表：`chat_message`

```java
package com.example.aichat.modules.chat.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chat_message")
public class ChatMessageDO extends BaseDO {

    @TableField("session_id")
    private Long sessionId;

    @TableField("user_id")
    private Long userId;

    @TableField("role")
    private String role;

    @TableField("seq_no")
    private Integer seqNo;

    @TableField("content")
    private String content;

    @TableField("content_format")
    private String contentFormat;

    @TableField("model_id")
    private Long modelId;

    @TableField("finish_reason")
    private String finishReason;

    @TableField("status")
    private Integer status;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("metadata")
    private String metadata;
}
```

### 6. ApiCallLogDO

对应表：`api_call_log`

```java
package com.example.aichat.modules.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("api_call_log")
public class ApiCallLogDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_id")
    private Long messageId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_id")
    private Long modelId;

    @TableField("request_id")
    private String requestId;

    @TableField("is_stream")
    private Integer isStream;

    @TableField("success_flag")
    private Integer successFlag;

    @TableField("http_status")
    private Integer httpStatus;

    @TableField("latency_ms")
    private Integer latencyMs;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("estimated_cost")
    private BigDecimal estimatedCost;

    @TableField("error_code")
    private String errorCode;

    @TableField("error_message")
    private String errorMessage;

    @TableField("request_payload")
    private String requestPayload;

    @TableField("response_payload")
    private String responsePayload;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

### 7. UserBalanceDO

对应表：`user_balance`

```java
package com.example.aichat.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_balance")
public class UserBalanceDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("balance_amount")
    private BigDecimal balanceAmount;

    @TableField("total_recharged")
    private BigDecimal totalRecharged;

    @TableField("total_consumed")
    private BigDecimal totalConsumed;

    @TableField("currency")
    private String currency;

    @TableField("status")
    private Integer status;
}
```

### 8. UserTokenUsageDO

对应表：`user_token_usage`

```java
package com.example.aichat.modules.billing.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_token_usage")
public class UserTokenUsageDO extends BaseDO {

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private Long sessionId;

    @TableField("message_id")
    private Long messageId;

    @TableField("api_call_log_id")
    private Long apiCallLogId;

    @TableField("provider_id")
    private Long providerId;

    @TableField("model_id")
    private Long modelId;

    @TableField("prompt_tokens")
    private Integer promptTokens;

    @TableField("completion_tokens")
    private Integer completionTokens;

    @TableField("total_tokens")
    private Integer totalTokens;

    @TableField("estimated_cost")
    private BigDecimal estimatedCost;

    @TableField("stat_date")
    private LocalDate statDate;
}
```

## 提示词模板的两种实现方案

### 方案 A：第一版推荐

直接放在配置文件或枚举里，不单独建表。

优点：

- 实现快
- 适合 MVP
- 不需要后台管理页

适合当前阶段。

### 方案 B：后续平台化

新增 `prompt_template` 表，并增加对应 Entity / Mapper。

建议字段可以包括：

- `id`
- `template_code`
- `template_name`
- `mode_code`
- `template_content`
- `status`
- `remark`
- `created_at`
- `updated_at`

用途：

- `quick` 绑定默认模板
- `expert` 绑定默认模板
- 后续支持运营后台动态修改模板
- 支持按模型、按业务场景扩展模板

## PromptTemplateDO 示例

如果后面走数据库表方案，可参考：

```java
package com.example.aichat.modules.prompt.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.common.model.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplateDO extends BaseDO {

    @TableField("template_code")
    private String templateCode;

    @TableField("template_name")
    private String templateName;

    @TableField("mode_code")
    private String modeCode;

    @TableField("template_content")
    private String templateContent;

    @TableField("status")
    private Integer status;

    @TableField("remark")
    private String remark;
}
```

## PromptTemplateMapper 示例

```java
package com.example.aichat.modules.prompt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.prompt.entity.PromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateDO> {
}
```

推荐后续自定义方法：

- `selectByTemplateCode(String templateCode)`
- `selectDefaultByModeCode(String modeCode)`

## Mapper 接口设计

第一版建议所有基础 Mapper 直接继承 `BaseMapper<T>`，复杂查询再单独补 XML。

## 通用写法

```java
package com.example.aichat.modules.xxx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.xxx.entity.XxxDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface XxxMapper extends BaseMapper<XxxDO> {
}
```

## 各实体对应 Mapper

### 1. AppUserMapper

```java
package com.example.aichat.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.user.entity.AppUserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUserDO> {
}
```

推荐后续自定义方法：

- `selectByUsername(String username)`
- `selectByEmail(String email)`
- `selectByMobile(String mobile)`

### 2. ModelProviderMapper

```java
package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ModelProviderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelProviderMapper extends BaseMapper<ModelProviderDO> {
}
```

推荐后续自定义方法：

- `selectByProviderCode(String providerCode)`
- `selectEnabledProviders()`

### 3. ModelConfigMapper

```java
package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ModelConfigDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigDO> {
}
```

推荐后续自定义方法：

- `selectByModelCode(String modelCode)`
- `selectEnabledModels()`
- `selectByProviderId(Long providerId)`

### 4. ChatSessionMapper

```java
package com.example.aichat.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.chat.entity.ChatSessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionDO> {
}
```

推荐后续自定义方法：

- `selectPageByUserId(Long userId)`
- `selectActiveById(Long sessionId, Long userId)`
- `updateLastMessageAt(Long sessionId, LocalDateTime lastMessageAt)`

### 5. ChatMessageMapper

```java
package com.example.aichat.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.chat.entity.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
```

推荐后续自定义方法：

- `selectBySessionIdOrderBySeqNo(Long sessionId)`
- `selectLatestMessages(Long sessionId, Integer limit)`
- `selectMaxSeqNo(Long sessionId)`

`selectMaxSeqNo` 很有用，新增消息时可以安全生成下一条序号。

### 6. ApiCallLogMapper

```java
package com.example.aichat.modules.model.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.model.entity.ApiCallLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiCallLogMapper extends BaseMapper<ApiCallLogDO> {
}
```

推荐后续自定义方法：

- `selectByRequestId(String requestId)`
- `selectRecentFailures(Integer limit)`
- `selectStatsByModelId(Long modelId)`

### 7. UserBalanceMapper

```java
package com.example.aichat.modules.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.billing.entity.UserBalanceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserBalanceMapper extends BaseMapper<UserBalanceDO> {
}
```

推荐后续自定义方法：

- `selectByUserId(Long userId)`

当前阶段虽然不做额度限制，但这个查询后面肯定会用到。

### 8. UserTokenUsageMapper

```java
package com.example.aichat.modules.billing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.modules.billing.entity.UserTokenUsageDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTokenUsageMapper extends BaseMapper<UserTokenUsageDO> {
}
```

推荐后续自定义方法：

- `selectDailyUsage(Long userId, LocalDate statDate)`
- `sumTotalTokensByUserId(Long userId)`
- `sumEstimatedCostByUserId(Long userId)`

## 推荐的 XML 使用策略

简单 CRUD 直接用 MyBatis-Plus，自定义查询再加 XML。

推荐路径：

```text
src/main/resources/mapper/user/AppUserMapper.xml
src/main/resources/mapper/chat/ChatSessionMapper.xml
src/main/resources/mapper/chat/ChatMessageMapper.xml
src/main/resources/mapper/model/ModelConfigMapper.xml
src/main/resources/mapper/model/ApiCallLogMapper.xml
src/main/resources/mapper/billing/UserTokenUsageMapper.xml
```

## 适合写 XML 的场景

- 多表联查
- 统计查询
- 聚合报表
- 分页列表包含模型名称、供应商名称
- 查询会话列表时顺带返回最后一条消息摘要

## ChatMessageMapper.xml 示例

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.aichat.modules.chat.mapper.ChatMessageMapper">

    <select id="selectMaxSeqNo" resultType="java.lang.Integer">
        SELECT COALESCE(MAX(seq_no), 0)
        FROM chat_message
        WHERE session_id = #{sessionId}
    </select>

    <select id="selectBySessionIdOrderBySeqNo"
            resultType="com.example.aichat.modules.chat.entity.ChatMessageDO">
        SELECT *
        FROM chat_message
        WHERE session_id = #{sessionId}
        ORDER BY seq_no ASC
    </select>

</mapper>
```

## 推荐的 Service 与 Mapper 分工

### Mapper 负责

- 单表 CRUD
- 明确的联表查询
- 聚合统计 SQL

### Service 负责

- 业务校验
- 表间关联校验
- 事务控制
- 会话和消息的一致性处理
- 模式提示词解析和组装
- token 记录、调用日志记录

## 聊天主流程里的调用顺序建议

### 发消息时

1. `ChatSessionMapper` 校验会话是否存在且属于当前用户
2. `ModelConfigMapper` 校验模型是否存在且启用
3. `ChatPromptResolver` 根据 `modeCode` 解析默认提示词模板
4. `ChatMessageMapper.selectMaxSeqNo(sessionId)` 获取下一条顺序号
5. 插入用户消息
6. 调用模型接口
7. 插入 assistant 消息
8. 插入 `ApiCallLog`
9. 插入 `UserTokenUsage`
10. 更新 `ChatSession.lastMessageAt`

## 推荐补充的枚举

建议你在 `common.enums` 里加这些枚举，避免直接写魔法字符串：

- `UserStatusEnum`
- `CommonStatusEnum`
- `ChatRoleEnum`
- `ChatModeEnum`
- `MessageStatusEnum`
- `ModelTypeEnum`
- `FinishReasonEnum`

例如：

```java
public enum ChatRoleEnum {
    SYSTEM,
    USER,
    ASSISTANT
}
```

## 推荐的 application.yml 配置

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/ai_chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  mapper-locations: classpath:/mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

## 第一版落地建议

如果你下一步就要开始建后端工程，建议优先创建下面这些文件：

- `BaseDO`
- `AppUserDO`
- `ChatSessionDO`
- `ChatMessageDO`
- `ModelProviderDO`
- `ModelConfigDO`
- `ApiCallLogDO`
- `AppUserMapper`
- `ChatSessionMapper`
- `ChatMessageMapper`
- `ModelProviderMapper`
- `ModelConfigMapper`
- `ApiCallLogMapper`

余额和 token 统计相关可以一起建，但业务逻辑先不启用限制。

## 当前这份设计的取舍

- 选择 `MyBatis-Plus`，是为了快速起步和减少样板代码
- `JSON` 字段先映射为 `String`，首版最稳
- 不在实体层做强关联对象嵌套，避免后续联表复杂化
- 不依赖数据库外键，改由 Service 层做关系校验
- `quick / expert` 优先通过提示词模板驱动，而不是写死成两套业务流程

如果后面你准备真正开后端项目，我下一步最合适的是继续补下面两项之一：

1. 直接帮你生成一套 `Spring Boot` 项目骨架代码
2. 先输出 `聊天模块接口文档 + DTO 设计`
