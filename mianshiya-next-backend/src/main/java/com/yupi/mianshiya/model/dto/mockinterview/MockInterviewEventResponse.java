package com.yupi.mianshiya.model.dto.mockinterview;

import com.yupi.mianshiya.model.entity.MockInterview;
import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试事件响应
 */
@Data
public class MockInterviewEventResponse implements Serializable {

    /**
     * AI 最终回复
     */
    private String aiResponse;

    /**
     * 更新后的面试快照
     */
    private MockInterview mockInterview;

    private static final long serialVersionUID = 1L;
}
