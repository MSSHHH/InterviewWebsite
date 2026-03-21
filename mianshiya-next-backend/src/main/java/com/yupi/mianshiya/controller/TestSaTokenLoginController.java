package com.yupi.mianshiya.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sa-Token 登录演示控制器（仅用于本地调试 / 教学演示）。
 *
 * 注意：
 * - 该控制器使用硬编码账号密码，仅用于验证认证流程是否打通；
 * - 生产环境不应依赖该控制器，真实登录必须走 UserController + 数据库校验。
 */
@RestController
@RequestMapping("/test/user")
public class TestSaTokenLoginController {

    // 测试登录，浏览器访问： http://localhost:8101/api/test/user/doLogin?username=zhang&password=123456
    @RequestMapping("doLogin")
    public String doLogin(String username, String password) {
        // 此处仅作模拟示例，真实项目需要从数据库中查询数据进行比对
        if("zhang".equals(username) && "123456".equals(password)) {
            StpUtil.login(10001);
            return "登录成功";
        }
        return "登录失败";
    }

    // 查询登录状态，浏览器访问： http://localhost:8101/api/test/user/isLogin
    @RequestMapping("isLogin")
    public String isLogin() {
        return "当前会话是否登录：" + StpUtil.isLogin();
    }

}
