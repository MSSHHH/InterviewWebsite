package com.yupi.mianshiya.model.dto.questionBankQuestion;

import com.yupi.mianshiya.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询题库题目关联请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * 关联记录 id（精确查询）。
     */
    private Long id;

    /**
     * 排除的关联记录 id。
     */
    private Long notId;

    /**
     * 题库 id
     */
    private Long questionBankId;

    /**
     * 题目 id
     */
    private Long questionId;

    /**
     * 创建该关联关系的用户 id（审计 / 过滤场景）。
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
