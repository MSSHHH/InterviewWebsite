package com.yupi.mianshiya.model.dto.mockinterview;

import lombok.Data;

import java.io.Serializable;

/**
 * 模拟面试单条消息模型。
 *
 * 用于序列化到 MockInterview.messages(JSON) 字段中，
 * 保存完整对话上下文，便于后续继续追问和总结。
 */
@Data
public class MockInterviewChatMessage implements Serializable {

    private static final long serialVersionUID = -2056799733159215147L;

    /**
     * 发送角色（例如：system / interviewer / user）。
     */
    private String role;

    /**
     * 消息内容
     */
    private String message;

}
