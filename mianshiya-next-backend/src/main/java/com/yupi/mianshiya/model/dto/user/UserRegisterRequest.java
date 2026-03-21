package com.yupi.mianshiya.model.dto.user;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户注册请求体
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 登录账号（注册后唯一）。
     */
    private String userAccount;

    /**
     * 登录密码（明文仅用于传输，服务端会加密后入库）。
     */
    private String userPassword;

    /**
     * 二次确认密码（用于前后端一致性校验）。
     */
    private String checkPassword;
}
