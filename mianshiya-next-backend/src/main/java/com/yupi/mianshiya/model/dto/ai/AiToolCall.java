package com.yupi.mianshiya.model.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 工具调用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolCall implements Serializable {

    /**
     * 工具调用 id
     */
    private String id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具参数（JSON 字符串）
     */
    private String arguments;

    private static final long serialVersionUID = 1L;
}
