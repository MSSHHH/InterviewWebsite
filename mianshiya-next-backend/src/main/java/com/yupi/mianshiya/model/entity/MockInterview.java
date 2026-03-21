package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 模拟面试实体（mock_interview 表）。
 *
 * 该表记录一次完整模拟面试会话，包括：
 * - 面试上下文（岗位、年限、难度）；
 * - 对话消息历史（messages JSON）；
 * - 面试状态流转（待开始 -> 进行中 -> 已结束）。
 *
 * @TableName mock_interview
 */
@TableName(value ="mock_interview")
@Data
public class MockInterview implements Serializable {
    /**
     * 主键 id（自增）。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工作年限（用于约束 AI 出题深度）。
     */
    private String workExperience;

    /**
     * 面试岗位（如后端开发、前端开发）。
     */
    private String jobPosition;

    /**
     * 面试难度（如初级 / 中级 / 高级）。
     */
    private String difficulty;

    /**
     * 对话消息列表（JSON 数组字符串）。
     * 通常包含 system / interviewer / user 多角色消息，
     * 面试结束时还会附带总结信息。
     */
    private String messages;

    /**
     * 面试状态：0-待开始、1-进行中、2-已结束。
     */
    private Integer status;

    /**
     * 创建该面试会话的用户 id。
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
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
