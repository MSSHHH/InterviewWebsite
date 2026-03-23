package com.yupi.mianshiya.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 结构化生成单道题目
 */
@Data
public class GeneratedQuestionItem implements Serializable {

    /**
     * 题目标题
     */
    private String title;

    private static final long serialVersionUID = 1L;
}
