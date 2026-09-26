package org.shiyi.memo.auth;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AppProperties properties;

    /**
     * 只有配置里打开邮件、并且填了账号密码，才算真正能发信。
     */
    public boolean isMailEnabled() {
        return properties.getMail().isEnabled()
                && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendLoginCode(String email, String code, int ttlMinutes) {
        if (!isMailEnabled()) {
            log.warn("邮件服务未启用，验证码没有发出。email={} code={}（仅本地调试用，公网部署请配置 SMTP 并关闭 app.mail.expose-code）",
                    email, code);
            return;
        }
        String text = """
                你正在登录「拾忆」。

                验证码：%s
                有效期：%d 分钟

                如果不是你本人操作，忽略这封邮件即可，任何人拿到验证码都能登录，请勿转发。
                """.formatted(code, ttlMinutes);
        try {
            MimeMessage message = mailSenderProvider.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            String from = properties.getMail().getFrom();
            helper.setTo(email);
            helper.setSubject("【拾忆】登录验证码 " + code);
            helper.setText(text, false);
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            mailSenderProvider.getObject().send(message);
            log.info("验证码邮件已发送 email={}", email);
        } catch (Exception ex) {
            log.error("验证码邮件发送失败 email={}", email, ex);
            throw new IllegalStateException("验证码邮件发送失败，请稍后重试或联系管理员");
        }
    }

    /** 密码被设置或修改后发一封通知，账号被盗时能第一时间发现 */
    public void sendPasswordChanged(String email, boolean changed) {
        if (!isMailEnabled()) {
            log.warn("邮件服务未启用，密码{}通知没有发出。email={}", changed ? "修改" : "设置", email);
            return;
        }
        String action = changed ? "修改" : "设置";
        String text = """
                你的账号 %s 刚刚%s了登录密码。

                如果这不是你本人操作，请立刻用邮箱验证码登录并重新设置密码。
                """.formatted(email, action);
        try {
            MimeMessage message = mailSenderProvider.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(email);
            helper.setSubject("【拾忆】登录密码已" + action);
            helper.setText(text, false);
            String from = properties.getMail().getFrom();
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            mailSenderProvider.getObject().send(message);
            log.info("密码{}通知已发送 email={}", action, email);
        } catch (Exception ex) {
            // 通知失败不影响“密码已经改成功”这个事实
            log.error("密码{}通知发送失败 email={}", action, email, ex);
        }
    }
}
