package com.yupi.mianshiya.model.dto.post;

import com.yupi.mianshiya.common.PageRequest;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PostQueryRequest extends PageRequest implements Serializable {

    /**
     * 指定帖子 id（精确查询）。
     */
    private Long id;

    /**
     * 排除帖子 id（相关推荐场景常用）。
     */
    private Long notId;

    /**
     * 通用搜索词（通常匹配标题、正文、描述等字段）。
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
     * “或”标签集合：命中任意一个标签即可。
     */
    private List<String> orTags;

    /**
     * 发帖用户 id（按作者过滤）。
     */
    private Long userId;

    /**
     * 收藏该帖子的用户 id（用于“我收藏的帖子”查询）。
     */
    private Long favourUserId;

    private static final long serialVersionUID = 1L;
}
