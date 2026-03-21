package com.yupi.mianshiya.model.dto.questionBank;

import com.yupi.mianshiya.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询题库请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://www.code-nav.cn">编程导航学习圈</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class QuestionBankQueryRequest extends PageRequest implements Serializable {

    /**
     * 指定题库 id（精确查询）。
     */
    private Long id;

    /**
     * 排除的题库 id（用于相关推荐等排除场景）。
     */
    private Long notId;

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 标题
     */
    private String title;

    /**
     * 描述
     */
    private String description;

    /**
     * 图片
     */
    private String picture;

    /**
     * 创建者用户 id（按作者筛选题库）。
     */
    private Long userId;

    /**
     * 是否在返回题库详情时联带题目分页数据。
     * - false：只查题库元信息（更快）
     * - true：额外查询题目列表（详情页场景）
     */
    private boolean needQueryQuestionList;

    private static final long serialVersionUID = 1L;
}
