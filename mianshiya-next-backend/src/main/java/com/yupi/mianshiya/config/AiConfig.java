package com.yupi.mianshiya.config;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Configuration
@ConfigurationProperties(prefix = "ai")
@Data
public class AiConfig {

    // ApiKey
    private String apiKey;

    /**
     * 兼容 OpenAI 协议的基础地址，默认使用阿里云百炼兼容模式。
     */
    private String baseUrl;

    /**
     * 默认模型。
     */
    private String model;

}
