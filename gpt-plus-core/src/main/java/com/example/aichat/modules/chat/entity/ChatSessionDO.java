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
