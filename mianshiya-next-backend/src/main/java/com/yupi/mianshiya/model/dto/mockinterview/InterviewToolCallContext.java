package com.yupi.mianshiya.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单次模拟面试工具调用上下文
 */
@Data
public class InterviewToolCallContext implements Serializable {

    private Long mockInterviewId;

    private String targetDifficulty;

    private Long currentQuestionId;

    private String currentQuestionTitle;

    private String currentQuestionDifficulty;

    private final Set<Long> askedQuestionIds = new LinkedHashSet<>();

    private boolean ended;

    private String finalMessage;

    private MockInterviewReport report;

    private String lastToolName;

    private static final long serialVersionUID = 1L;
}
