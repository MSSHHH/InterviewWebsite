package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 题库实体（question_bank 表）。
 *
 * 题库是题目的上层组织维度，用于把同类题目聚合成学习专题。
 * 题目与题库的关系通过 question_bank_question 关联表维护。
 *
 * @TableName question_bank
 */
@TableName(value ="question_bank")
@Data
public class QuestionBank implements Serializable {
    /**
     * 主键 id（雪花算法生成）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题库标题（前台列表和详情页主展示字段）。
     */
    private String title;

    /**
     * 题库描述（用于介绍学习范围、难度、适用人群）。
     */
    private String description;

    /**
     * 题库封面图片 URL。
     */
    private String picture;

    /**
     * 创建人用户 id（用于权限判定：本人可编辑）。
     */
    private Long userId;

    /**
     * 最近业务编辑时间。
     */
    private Date editTime;

    /**
     * 记录创建时间（系统字段）。
     */
    private Date createTime;

    /**
     * 记录更新时间（系统字段）。
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
