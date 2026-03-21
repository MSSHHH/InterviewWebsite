package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 帖子点赞明细实体（post_thumb 表）。
 *
 * 一条记录代表“某用户点赞了某帖子”。
 * 与 post.thumbNum 配合：本表记录行为明细，post 表保存聚合计数。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@TableName(value = "post_thumb")
@Data
public class PostThumb implements Serializable {

    /**
     * 主键 id。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 帖子 id（关联 post.id）。
     */
    private Long postId;

    /**
     * 点赞用户 id（谁点了赞）。
     */
    private Long userId;

    /**
     * 点赞操作时间。
     */
    private Date createTime;

    /**
     * 记录更新时间。
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
