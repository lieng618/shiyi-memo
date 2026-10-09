package org.shiyi.memo.friend;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.SimpleRateLimiter;
import org.shiyi.memo.common.WebUtils;
import org.shiyi.memo.friend.dto.FriendDtos;
import org.shiyi.memo.security.PublicApi;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.entity.AppUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 邀请链接的落地页数据：/j/{code} 打开后先看「谁邀请你」，登录后再发好友申请。
 * 免登录接口，所以只回昵称，不给邮箱、头像这些能拼出身份的东西。
 */
@PublicApi
@RestController
@RequestMapping("/api/public/invite")
@RequiredArgsConstructor
public class InviteController {

    private final UserService userService;
    private final SimpleRateLimiter rateLimiter;

    @GetMapping("/{code}")
    public FriendDtos.InviteCardDto card(@PathVariable String code, HttpServletRequest request) {
        // 码空间够大，扫不出来；限流是挡脚本乱试和异常流量
        if (!rateLimiter.tryAcquire("invite:ip:" + WebUtils.clientIp(request), 30,
                Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("请求过于频繁，请稍后再试");
        }
        AppUser inviter = userService.findByInviteCode(code);
        if (inviter == null || !inviter.isEnabled()) {
            throw ApiException.notFound("邀请链接无效");
        }
        return FriendDtos.InviteCardDto.from(inviter);
    }
}
