package com.yupi.mianshiya.model.dto.question;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 生成题目请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@Data
public class QuestionAIGenerateRequest implements Serializable {

    /**
     * 题目方向 / 类型，例如：Java、Spring、MySQL、Redis。
     * 会作为 Prompt 的输入，引导 AI 按该方向生成题目。
     */
    private String questionType;

    /**
     * 期望生成数量，默认 10。
     * 建议控制在较小范围，避免单次生成耗时过长。
     */
    private int number = 10;

    private static final long serialVersionUID = 1L;
}
