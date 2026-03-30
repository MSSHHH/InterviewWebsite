package com.yupi.mianshiya.model.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 会话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage implements Serializable {

    /**
     * 消息角色
     */
    private AiChatMessageRole role;

    /**
     * 文本内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}
