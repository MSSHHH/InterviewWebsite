package com.yupi.mianshiya.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试事件请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Data
public class MockInterviewEventRequest implements Serializable {

    /**
     * 事件类型（取值：start / chat / end）。
     * - start：开始面试
     * - chat：继续对话
     * - end：结束面试并输出总结
     */
    private String event;

    /**
     * 用户消息内容（event=chat 时必填）。
     */
    private String message;

    /**
     * 模拟面试会话 id（即 mockInterview.id）。
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
