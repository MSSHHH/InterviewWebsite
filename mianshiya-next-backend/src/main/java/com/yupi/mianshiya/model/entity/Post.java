package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 帖子实体（post 表）。
 *
 * 用于承载社区内容（标题、正文、标签）及互动统计（点赞数、收藏数）。
 * 互动明细由 post_thumb / post_favour 记录，本表只保存聚合计数。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@TableName(value = "post")
@Data
public class Post implements Serializable {

    /**
     * 主键 id（雪花算法生成）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 帖子标题。
     */
    private String title;

    /**
     * 帖子正文内容（Markdown / 富文本原文）。
     */
    private String content;

    /**
     * 标签列表（JSON 数组字符串）。
     */
    private String tags;

    /**
     * 点赞总数（聚合字段，便于列表快速展示）。
     */
    private Integer thumbNum;

    /**
     * 收藏总数（聚合字段，便于列表快速展示）。
     */
    private Integer favourNum;

    /**
     * 发帖用户 id。
     */
    private Long userId;

    /**
     * 记录创建时间。
     */
    private Date createTime;

    /**
     * 记录更新时间。
     */
    private Date updateTime;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）。
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
