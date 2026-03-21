package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 题库-题目关系实体（question_bank_question 表）。
 *
 * 该表用于维护多对多关系：
 * - 一个题库可包含多道题；
 * - 一道题也可被多个题库复用。
 *
 * @TableName question_bank_question
 */
@TableName(value ="question_bank_question")
@Data
public class QuestionBankQuestion implements Serializable {
    /**
     * 主键 id（雪花算法生成）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 题库 id（关联 question_bank.id）。
     */
    private Long questionBankId;

    /**
     * 题目 id（关联 question.id）。
     */
    private Long questionId;

    /**
     * 创建该关联关系的用户 id（便于审计来源）。
     */
    private Long userId;

    /**
     * 关系创建时间。
     */
    private Date createTime;

    /**
     * 关系更新时间。
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
