package com.yupi.mianshiya.model.dto.question;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 结构化生成题目结果
 */
@Data
public class GeneratedQuestionBatch implements Serializable {

    /**
     * 结构化题目列表
     */
    private List<GeneratedQuestionItem> questions;

    private static final long serialVersionUID = 1L;
}
