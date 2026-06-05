SET NAMES utf8mb4;

CREATE TABLE `app_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `mobile` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0禁用',
  `last_login_at` DATETIME DEFAULT NULL COMMENT '最后登录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_username` (`username`),
  UNIQUE KEY `uk_app_user_email` (`email`),
  UNIQUE KEY `uk_app_user_mobile` (`mobile`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';


CREATE TABLE `model_provider` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_code` VARCHAR(64) NOT NULL COMMENT '供应商编码，如 openai/deepseek/anthropic',
  `provider_name` VARCHAR(128) NOT NULL COMMENT '供应商名称',
  `base_url` VARCHAR(255) DEFAULT NULL COMMENT '接口基础地址',
  `api_key_encrypted` VARCHAR(1024) DEFAULT NULL COMMENT '加密后的API Key',
  `default_headers` JSON DEFAULT NULL COMMENT '默认请求头JSON',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_provider_code` (`provider_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型供应商表';


CREATE TABLE `model_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `model_code` VARCHAR(128) NOT NULL COMMENT '模型编码，如 gpt-4o/deepseek-v4-flash',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型显示名',
  `model_type` VARCHAR(32) NOT NULL DEFAULT 'chat' COMMENT '模型类型: chat/embedding/rerank',
  `support_stream` TINYINT NOT NULL DEFAULT 1 COMMENT '是否支持流式',
  `support_thinking` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持思考模式',
  `support_json_output` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持JSON Output',
  `support_vision` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持图片',
  `support_file` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持文件',
  `context_window` INT DEFAULT NULL COMMENT '上下文窗口大小',
  `max_output_tokens` INT DEFAULT NULL COMMENT '最大输出token',
  `temperature_default` DECIMAL(4,2) DEFAULT 0.70 COMMENT '默认temperature',
  `top_p_default` DECIMAL(4,2) DEFAULT 1.00 COMMENT '默认top_p',
  `extra_config` JSON DEFAULT NULL COMMENT '扩展配置JSON',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0停用',
  `sort_no` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_config_code` (`model_code`),
  KEY `idx_model_config_provider_id` (`provider_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';


CREATE TABLE `chat_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `title` VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
  `mode_code` VARCHAR(32) NOT NULL DEFAULT 'quick' COMMENT '会话模式: quick/expert',
  `default_model_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '默认模型ID',
  `system_prompt` TEXT DEFAULT NULL COMMENT '系统提示词',
  `last_message_at` DATETIME DEFAULT NULL COMMENT '最后消息时间',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0删除/归档',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_session_user_id` (`user_id`),
  KEY `idx_chat_session_last_message_at` (`last_message_at`),
  KEY `idx_chat_session_default_model_id` (`default_model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';


CREATE TABLE `chat_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
  `role` VARCHAR(16) NOT NULL COMMENT '角色: system/user/assistant',
  `seq_no` INT NOT NULL COMMENT '会话内消息顺序',
  `content` LONGTEXT NOT NULL COMMENT '消息内容',
  `content_format` VARCHAR(16) NOT NULL DEFAULT 'markdown' COMMENT '内容格式: plain/markdown/json',
  `model_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '本条消息使用的模型ID',
  `finish_reason` VARCHAR(32) DEFAULT NULL COMMENT '结束原因 stop/length/error',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0失败 2中断',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总token',
  `metadata` JSON DEFAULT NULL COMMENT '扩展信息JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_message_session_seq` (`session_id`, `seq_no`),
  KEY `idx_chat_message_user_id` (`user_id`),
  KEY `idx_chat_message_model_id` (`model_id`),
  KEY `idx_chat_message_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';


CREATE TABLE `file_asset` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '上传用户ID',
  `biz_type` VARCHAR(32) NOT NULL DEFAULT 'chat_image' COMMENT '业务类型: chat_image',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `content_type` VARCHAR(64) NOT NULL COMMENT '文件MIME类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小字节数',
  `storage_path` VARCHAR(512) NOT NULL COMMENT '服务端存储路径',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '原始OSS地址或本地业务读取地址',
  `signed_url` VARCHAR(2048) DEFAULT NULL COMMENT '可直接访问的OSS签名地址',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_asset_user_id` (`user_id`),
  KEY `idx_file_asset_biz_type` (`biz_type`),
  KEY `idx_file_asset_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件附件表';


CREATE TABLE `api_call_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `session_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '会话ID',
  `message_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联的消息ID',
  `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `model_id` BIGINT UNSIGNED NOT NULL COMMENT '模型ID',
  `request_id` VARCHAR(128) DEFAULT NULL COMMENT '请求流水号',
  `is_stream` TINYINT NOT NULL DEFAULT 1 COMMENT '是否流式',
  `success_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功',
  `http_status` INT DEFAULT NULL COMMENT 'HTTP状态码',
  `latency_ms` INT DEFAULT NULL COMMENT '耗时毫秒',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总token',
  `estimated_cost` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '预估成本',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `request_payload` JSON DEFAULT NULL COMMENT '请求摘要JSON',
  `response_payload` JSON DEFAULT NULL COMMENT '响应摘要JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_api_call_log_user_id` (`user_id`),
  KEY `idx_api_call_log_session_id` (`session_id`),
  KEY `idx_api_call_log_message_id` (`message_id`),
  KEY `idx_api_call_log_provider_id` (`provider_id`),
  KEY `idx_api_call_log_model_id` (`model_id`),
  KEY `idx_api_call_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型调用日志表';


CREATE TABLE `user_balance` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `balance_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '当前余额',
  `total_recharged` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '累计充值',
  `total_consumed` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '累计消费',
  `currency` VARCHAR(16) NOT NULL DEFAULT 'CNY' COMMENT '币种',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0冻结',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_balance_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户余额表(首版只预留，不做拦截)';


CREATE TABLE `user_token_usage` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  `session_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '会话ID',
  `message_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '消息ID',
  `api_call_log_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '调用日志ID',
  `provider_id` BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  `model_id` BIGINT UNSIGNED NOT NULL COMMENT '模型ID',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总token',
  `estimated_cost` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '预估成本',
  `stat_date` DATE NOT NULL COMMENT '统计日期',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_token_usage_user_id` (`user_id`),
  KEY `idx_user_token_usage_stat_date` (`stat_date`),
  KEY `idx_user_token_usage_session_id` (`session_id`),
  KEY `idx_user_token_usage_message_id` (`message_id`),
  KEY `idx_user_token_usage_api_call_log_id` (`api_call_log_id`),
  KEY `idx_user_token_usage_provider_id` (`provider_id`),
  KEY `idx_user_token_usage_model_id` (`model_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户token消耗表(首版只记录，不做额度限制)';
