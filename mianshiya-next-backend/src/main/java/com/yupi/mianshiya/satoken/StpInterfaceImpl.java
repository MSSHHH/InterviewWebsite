package com.yupi.mianshiya.satoken;

import cn.dev33.satoken.stp.StpInterface;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.service.UserService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

/**
 * 自定义权限加载接口实现类
 */
@Component // 保证此类被 SpringBoot 扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserService userService;

    /**
     * 返回一个账号所拥有的权限码集合（目前没用）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userService.getById(String.valueOf(loginId));
        if (user == null || user.getUserRole() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(user.getUserRole());
    }

}
