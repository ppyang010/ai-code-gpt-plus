package com.example.aichat.common.enums;

import java.util.Arrays;

public enum ChatModeEnum {
    QUICK("quick"),
    EXPERT("expert");

    private final String code;

    ChatModeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ChatModeEnum fromCodeOrDefault(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(QUICK);
    }
}
