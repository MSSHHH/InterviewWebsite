package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 题目实体（question 表）。
 *
 * 该实体是“题目主数据”的真实来源（Source of Truth）：
 * - ES 检索只做召回，最终展示数据会回到该表获取；
 * - 高频变更字段以数据库为准，降低检索索引维护成本。
 *
 * @TableName question
 */
@TableName(value ="question")
@Data
public class Question implements Serializable {
    /**
     * 主键 id（雪花算法生成）。
     * 同时作为 ES 文档 id，用于幂等覆盖同步。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题目标题（检索核心字段之一）。
     */
    private String title;

    /**
     * 题目正文（题干内容，检索核心字段之一）。
     */
    private String content;

    /**
     * 标签列表（JSON 数组字符串）。
     * 例如：["Java","并发","线程池"]。
     */
    private String tags;

    /**
     * 题目参考答案（可由管理员维护或 AI 生成）。
     */
    private String answer;

    /**
     * 题目难度（easy / medium / hard）。
     */
    private String difficulty;

    /**
     * 创建人用户 id（用于数据归属与权限校验）。
     */
    private Long userId;

    /**
     * 最近一次业务编辑时间（区别于系统 updateTime，可用于运营排序）。
     */
    private Date editTime;

    /**
     * 记录创建时间（系统字段）。
     */
    private Date createTime;

    /**
     * 记录更新时间（系统字段，增量同步 ES 的核心依据）。
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
