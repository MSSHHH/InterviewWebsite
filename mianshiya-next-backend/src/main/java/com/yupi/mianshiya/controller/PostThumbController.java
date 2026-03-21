package com.yupi.mianshiya.controller;

import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.postthumb.PostThumbAddRequest;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.service.PostThumbService;
import com.yupi.mianshiya.service.UserService;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帖子点赞控制器。
 *
 * 主要职责：
 * 1) 处理“点赞 / 取消点赞”切换请求；
 * 2) 对外返回本次操作影响数量（+1 / -1 / 0）；
 * 3) 在接入层保证参数有效且用户已登录。
 *
 * 说明：
 * - 点赞统计是高频操作，控制器只做参数与鉴权；
 * - 并发安全、重复点击去重等策略在 Service 层实现。
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/post_thumb")
@Slf4j
public class PostThumbController {

    @Resource
    private PostThumbService postThumbService;

    @Resource
    private UserService userService;

    /**
     * 点赞 / 取消点赞
     *
     * @param postThumbAddRequest
     * @param request
     * @return resultNum 本次点赞变化数
     */
    @PostMapping("/")
    public BaseResponse<Integer> doThumb(@RequestBody PostThumbAddRequest postThumbAddRequest,
            HttpServletRequest request) {
        if (postThumbAddRequest == null || postThumbAddRequest.getPostId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录才能点赞
        final User loginUser = userService.getLoginUser(request);
        long postId = postThumbAddRequest.getPostId();
        int result = postThumbService.doPostThumb(postId, loginUser);
        return ResultUtils.success(result);
    }

}
