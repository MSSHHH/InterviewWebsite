package com.yupi.mianshiya.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模拟面试选题结果
 */
@Data
public class InterviewQuestionSearchResult implements Serializable {

    private Long id;

    private String title;

    private String difficulty;

    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
