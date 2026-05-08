package com.example.aichat.modules.chat.vo;

import java.util.List;
import lombok.Data;

@Data
public class ChatMessageListVO {

    private Long sessionId;
    private String title;
    private String modeCode;
    private Long defaultModelId;
    private List<ChatMessageItemVO> messageList;
}
