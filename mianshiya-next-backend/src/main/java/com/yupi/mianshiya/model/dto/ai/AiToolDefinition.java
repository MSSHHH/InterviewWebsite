package com.yupi.mianshiya.model.dto.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 工具定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiToolDefinition implements Serializable {

    /**
     * 工具名
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * JSON Schema 参数定义
     */
    private JsonNode parameters;

    private static final long serialVersionUID = 1L;
}
