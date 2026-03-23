package com.yupi.mianshiya.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模拟面试结构化报告
 */
@Data
public class MockInterviewReport implements Serializable {

    /**
     * 总分，建议范围 0 - 100
     */
    private Integer overallScore;

    /**
     * 总体总结
     */
    private String summary;

    /**
     * 优点列表
     */
    private List<String> strengths;

    /**
     * 问题列表
     */
    private List<String> weaknesses;

    /**
     * 建议列表
     */
    private List<String> suggestions;

    /**
     * 本次面试涉及的题目 id
     */
    private List<Long> questionIds;

    /**
     * 完成时间
     */
    private String finishedAt;

    private static final long serialVersionUID = 1L;
}
