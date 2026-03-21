package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 帖子收藏明细实体（post_favour 表）。
 *
 * 一条记录代表“某用户收藏了某帖子”。
 * 与 post.favourNum 配合使用：本表存明细，post 表存聚合统计。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 **/
@TableName(value = "post_favour")
@Data
public class PostFavour implements Serializable {

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
     * 收藏用户 id（谁收藏了该帖子）。
     */
    private Long userId;

    /**
     * 收藏操作时间。
     */
    private Date createTime;

    /**
     * 记录更新时间。
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
