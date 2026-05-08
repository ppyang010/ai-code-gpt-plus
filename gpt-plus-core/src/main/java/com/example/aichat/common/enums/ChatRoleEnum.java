package com.example.aichat.common.enums;

public enum ChatRoleEnum {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String code;

    ChatRoleEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
