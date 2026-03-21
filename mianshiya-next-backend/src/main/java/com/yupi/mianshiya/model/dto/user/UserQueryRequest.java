package com.yupi.mianshiya.model.dto.user;

import com.yupi.mianshiya.common.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询请求
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    /**
     * 用户 id（精确查询）。
     */
    private Long id;

    /**
     * 微信开放平台 unionId（跨应用唯一标识）。
     */
    private String unionId;

    /**
     * 微信公众号 openId（公众号内唯一标识）。
     */
    private String mpOpenId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户简介（支持模糊查询）。
     */
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}
