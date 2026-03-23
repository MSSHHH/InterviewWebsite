package com.yupi.mianshiya.model.enums;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目难度枚举
 */
public enum QuestionDifficultyEnum {

    EASY("简单", "easy"),
    MEDIUM("中等", "medium"),
    HARD("困难", "hard");

    private final String text;

    private final String value;

    QuestionDifficultyEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static List<String> getValues() {
        return Arrays.stream(values()).map(QuestionDifficultyEnum::getValue).collect(Collectors.toList());
    }

    public static boolean isValid(String value) {
        return StringUtils.isNotBlank(value) && getEnumByValue(value) != null;
    }

    public static QuestionDifficultyEnum getEnumByValue(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        for (QuestionDifficultyEnum questionDifficultyEnum : values()) {
            if (questionDifficultyEnum.value.equals(value)) {
                return questionDifficultyEnum;
            }
        }
        return null;
    }

    public String getText() {
        return text;
    }

    public String getValue() {
        return value;
    }
}
