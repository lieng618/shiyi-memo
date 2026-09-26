package org.shiyi.memo.auth;

import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.common.ApiException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 登录密码的编码与强度校验。只做哈希和校验，不掺业务。
 */
@Slf4j
@Service
public class PasswordService {

    private static final int MIN_LENGTH = 8;
    /** BCrypt 只取前 72 字节，超长部分会被忽略，所以直接限制上界 */
    private static final int MAX_LENGTH = 72;

    /** 常见弱密码，命中直接拒绝（不做复杂度强制，靠长度 + 黑名单） */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "12345678", "123456789", "1234567890", "password", "password1", "passw0rd",
            "qwertyui", "qwerty123", "abc12345", "11111111", "88888888", "00000000",
            "iloveyou", "admin123", "administrator", "woaini1314", "5201314520", "a1234567"
    );

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    /** 一个固定的假哈希：邮箱不存在时也走一次 compare，避免用响应时间猜出邮箱是否注册过 */
    private final String dummyHash = encoder.encode("not-a-real-password-for-timing-only");

    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }

    /** 用户不存在 / 没设密码时调用，消耗掉和真实校验差不多的计算时间 */
    public void wasteTime(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    public void validateStrength(String rawPassword, String email) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw ApiException.badRequest("请填写新密码");
        }
        if (rawPassword.length() < MIN_LENGTH) {
            throw ApiException.badRequest("密码至少 " + MIN_LENGTH + " 位");
        }
        if (rawPassword.length() > MAX_LENGTH) {
            throw ApiException.badRequest("密码最多 " + MAX_LENGTH + " 位");
        }
        String lower = rawPassword.toLowerCase(Locale.ROOT);
        if (WEAK_PASSWORDS.contains(lower)) {
            throw ApiException.badRequest("这个密码太常见了，换一个");
        }
        if (rawPassword.chars().distinct().count() <= 2) {
            throw ApiException.badRequest("密码太简单了，换一个");
        }
        if (email != null) {
            String localPart = email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length());
            if (localPart.length() >= 4 && lower.contains(localPart.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("密码不要包含你的邮箱名");
            }
        }
    }
}
