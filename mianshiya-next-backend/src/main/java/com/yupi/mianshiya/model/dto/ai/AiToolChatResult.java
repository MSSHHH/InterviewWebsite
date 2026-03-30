package com.yupi.mianshiya.model.dto.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 工具调用结果
 */
@Data
public class AiToolChatResult implements Serializable {

    /**
     * 模型请求 id
     */
    private String requestId;

    /**
     * 实际调用模型
     */
    private String model;

    /**
     * assistant 消息
     */
    private AiChatMessage assistantMessage;

    /**
     * tool calls
     */
    private List<AiToolCall> toolCalls;

    private static final long serialVersionUID = 1L;
}
