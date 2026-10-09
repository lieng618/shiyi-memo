package org.shiyi.memo.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.auth.dto.AuthDtos;
import org.shiyi.memo.auth.entity.EmailCode;
import org.shiyi.memo.auth.mapper.EmailCodeMapper;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.SimpleRateLimiter;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 邮箱验证码的发送与校验，目前用于邮箱登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    /** 校验失败次数上限，超过即作废；后台「验证码」页也用它判断状态 */
    public static final int MAX_ATTEMPTS = 5;
    private static final String PURPOSE_LOGIN = "LOGIN";

    private final EmailCodeMapper codeMapper;
    private final MailService mailService;
    private final AppProperties properties;
    private final SimpleRateLimiter rateLimiter;
    private final UserService userService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public AuthDtos.SendCodeResponse send(String rawEmail, String ip) {
        String email = UserService.normalizeEmail(rawEmail);
        AppProperties.Mail mail = properties.getMail();

        if (!rateLimiter.tryAcquire("code:ip:" + ip, 10, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("请求过于频繁，请稍后再试");
        }

        AppUser user = userService.findByEmail(email);
        if (user != null && !user.isEnabled()) {
            throw ApiException.forbidden("账号已被禁用，请联系管理员");
        }
        if (user == null && !properties.getSecurity().isAllowRegister()) {
            throw ApiException.forbidden("该邮箱尚未注册，当前站点未开放注册");
        }

        EmailCode latest = codeMapper.selectOne(Wrappers.lambdaQuery(EmailCode.class)
                .eq(EmailCode::getEmail, email)
                .orderByDesc(EmailCode::getId)
                .last("limit 1"));
        if (latest != null && latest.getCreatedAt() != null) {
            long seconds = Duration.between(latest.getCreatedAt(), LocalDateTime.now()).toSeconds();
            if (seconds < mail.getResendIntervalSeconds()) {
                throw ApiException.tooManyRequests(
                        "验证码已发送，请 " + (mail.getResendIntervalSeconds() - seconds) + " 秒后再试");
            }
        }
        Long todayCount = codeMapper.selectCount(Wrappers.lambdaQuery(EmailCode.class)
                .eq(EmailCode::getEmail, email)
                .ge(EmailCode::getCreatedAt, LocalDateTime.now().minusDays(1)));
        if (todayCount != null && todayCount >= mail.getDailyLimitPerEmail()) {
            throw ApiException.tooManyRequests("该邮箱今日验证码次数已用完，请明天再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailCode entity = new EmailCode();
        entity.setEmail(email);
        entity.setCode(code);
        entity.setPurpose(PURPOSE_LOGIN);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(mail.getCodeTtlMinutes()));
        entity.setAttempts(0);
        entity.setIp(ip);
        codeMapper.insert(entity);

        mailService.sendLoginCode(email, code, mail.getCodeTtlMinutes());

        boolean mailEnabled = mailService.isMailEnabled();
        String devCode = (!mailEnabled && mail.isExposeCode()) ? code : null;
        return new AuthDtos.SendCodeResponse(true, mailEnabled, devCode,
                mail.getCodeTtlMinutes() * 60, user != null);
    }

    /**
     * 校验并消费验证码；失败抛 ApiException。调用方不需要关心重试计数和过期。
     */
    @Transactional
    public void verifyAndConsume(String rawEmail, String inputCode) {
        String email = UserService.normalizeEmail(rawEmail);
        if (!rateLimiter.tryAcquire("code:verify:" + email, 20, Duration.ofMinutes(10))) {
            throw ApiException.tooManyRequests("尝试次数过多，请稍后再试");
        }
        if (inputCode == null || inputCode.isBlank()) {
            throw ApiException.badRequest("请填写验证码");
        }

        EmailCode latest = codeMapper.selectOne(Wrappers.lambdaQuery(EmailCode.class)
                .eq(EmailCode::getEmail, email)
                .isNull(EmailCode::getUsedAt)
                .orderByDesc(EmailCode::getId)
                .last("limit 1"));
        if (latest == null) {
            throw ApiException.badRequest("请先获取验证码");
        }
        if (latest.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("验证码已过期，请重新获取");
        }
        if (latest.getAttempts() != null && latest.getAttempts() >= MAX_ATTEMPTS) {
            throw ApiException.badRequest("验证码错误次数过多，请重新获取");
        }
        if (!latest.getCode().equals(inputCode.trim())) {
            latest.setAttempts(Optional.ofNullable(latest.getAttempts()).orElse(0) + 1);
            codeMapper.updateById(latest);
            throw ApiException.badRequest("验证码不正确");
        }
        latest.setUsedAt(LocalDateTime.now());
        codeMapper.updateById(latest);
    }
}
