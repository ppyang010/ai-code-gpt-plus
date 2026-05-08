package com.example.aichat.common.enums;

public enum MessageStatusEnum {
    FAILED(0),
    NORMAL(1),
    INTERRUPTED(2),
    GENERATING(3);

    private final int code;

    MessageStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
