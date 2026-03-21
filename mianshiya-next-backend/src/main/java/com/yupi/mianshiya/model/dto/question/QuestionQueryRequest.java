package com.yupi.mianshiya.model.dto.question;

import com.yupi.mianshiya.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 查询题目请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionQueryRequest extends PageRequest implements Serializable {

    /**
     * 指定题目 id（精确查询）。
     */
    private Long id;

    /**
     * 排除的题目 id（常用于“相关推荐排除当前题目”场景）。
     */
    private Long notId;

    /**
     * 通用搜索词。
     * 在 ES 中会匹配 title/content/answer 多字段。
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 题库 id（限定只查询某题库下的题目）。
     */
    private Long questionBankId;

    /**
     * 创建者用户 id（按出题人过滤）。
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
