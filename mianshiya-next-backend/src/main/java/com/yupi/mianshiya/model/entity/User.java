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
 * 用户实体（user 表）。
 *
 * 该实体承载平台账号体系、角色权限及用户画像信息。
 * 控制器 / Service 会基于 userRole、id 等字段做鉴权和数据归属判定。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@TableName(value = "user")
@Data
public class User implements Serializable {

    /**
     * 主键 id（雪花算法生成）。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 登录账号（站内唯一）。
     */
    private String userAccount;

    /**
     * 登录密码（数据库中存的是加密后的密文）。
     */
    private String userPassword;

    /**
     * 微信开放平台 unionId（跨应用统一身份标识）。
     */
    private String unionId;

    /**
     * 微信公众号 openId（公众号内唯一标识）。
     */
    private String mpOpenId;

    /**
     * 用户昵称（前台展示名称）。
     */
    private String userName;

    /**
     * 用户头像 URL。
     */
    private String userAvatar;

    /**
     * 用户简介（个人签名 / 自我介绍）。
     */
    private String userProfile;

    /**
     * 用户角色：user / admin / ban。
     */
    private String userRole;

    /**
     * 最近业务编辑时间。
     */
    private Date editTime;

    /**
     * 记录创建时间。
     */
    private Date createTime;

    /**
     * 记录更新时间。
     */
    private Date updateTime;

    /**
     * 手机号（用户补充信息）。
     */
    private String phoneNumber;

    /**
     * 邮箱（用户补充信息）。
     */
    private String email;

    /**
     * 年级（如 2022 级，用于学习阶段画像）。
     */
    private String grade;

    /**
     * 工作经验描述（如 3 年后端开发）。
     */
    private String workExperience;

    /**
     * 擅长方向（如 Java / Spring Cloud / 算法）。
     */
    private String expertiseDirection;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）。
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
