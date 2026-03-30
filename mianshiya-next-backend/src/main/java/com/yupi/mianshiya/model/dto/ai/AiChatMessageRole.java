package com.yupi.mianshiya.model.dto.ai;

/**
 * AI 会话消息角色
 */
public enum AiChatMessageRole {

    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String value;

    AiChatMessageRole(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AiChatMessageRole fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AiChatMessageRole role : values()) {
            if (role.value.equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }
}
