package org.shiyi.memo.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.admin.dto.AdminDtos;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.message.entity.WelcomeLetter;
import org.shiyi.memo.message.mapper.WelcomeLetterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 新用户欢迎信：后台配置内容，新注册的用户自动收到一条站内信。
 * 已发出去的信是当时那封的快照，改配置只影响之后注册的人。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeLetterService {

    /** 单行配置的固定主键 */
    private static final int SINGLETON_ID = 1;
    private static final String DEFAULT_SENDER = "拾忆";

    private final WelcomeLetterMapper welcomeLetterMapper;
    private final MessageService messageService;

    /** 后台读取配置；万一这行没了就回一份空配置，页面不至于报错 */
    public AdminDtos.WelcomeLetterDto get() {
        return toDto(welcomeLetterMapper.selectById(SINGLETON_ID));
    }

    @Transactional
    public AdminDtos.WelcomeLetterDto update(AdminDtos.WelcomeLetterRequest request, String adminName) {
        String title = request.title() == null ? "" : request.title().trim();
        String contentHtml = request.contentHtml() == null ? "" : request.contentHtml();
        if (Boolean.TRUE.equals(request.enabled()) && (title.isEmpty() || isBlankHtml(contentHtml))) {
            throw ApiException.badRequest("开启欢迎信前，请先填写标题和正文");
        }

        WelcomeLetter letter = new WelcomeLetter();
        letter.setId(SINGLETON_ID);
        letter.setEnabled(request.enabled());
        letter.setTitle(title);
        letter.setContentHtml(contentHtml);
        letter.setUpdatedBy(adminName);
        letter.setUpdatedAt(LocalDateTime.now());
        if (welcomeLetterMapper.selectById(SINGLETON_ID) == null) {
            welcomeLetterMapper.insert(letter);
        } else {
            welcomeLetterMapper.updateById(letter);
        }
        log.info("欢迎信配置已更新 enabled={} 操作人={}", letter.getEnabled(), adminName);
        return toDto(letter);
    }

    /**
     * 新用户注册成功后发一封欢迎信。
     * 单独开一个事务：这里出问题最多是少一封欢迎信，绝不能牵连注册——调用方还会再兜一层 catch。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendToNewUser(long userId, String nickname, String email) {
        WelcomeLetter letter = welcomeLetterMapper.selectById(SINGLETON_ID);
        if (letter == null || !Boolean.TRUE.equals(letter.getEnabled())) {
            return;
        }
        String title = letter.getTitle() == null ? "" : letter.getTitle().trim();
        String html = letter.getContentHtml();
        if (title.isEmpty() || isBlankHtml(html)) {
            log.warn("欢迎信已开启，但标题或正文是空的，跳过发送 userId={}", userId);
            return;
        }
        messageService.sendWelcome(userId, title, applyPlaceholders(html, nickname, email), DEFAULT_SENDER);
        log.info("新用户欢迎信已发送 userId={} email={}", userId, email);
    }

    /** 替换 {nickname} / {email}；值来自用户本人，必须转义后再拼进 HTML */
    static String applyPlaceholders(String html, String nickname, String email) {
        String name = nickname == null || nickname.isBlank() ? "朋友" : nickname;
        return html.replace("{nickname}", escapeHtml(name))
                .replace("{email}", escapeHtml(email == null ? "" : email));
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** 富文本判空：只有标签和空行也算空，但插了图 / 视频 / 音频不算 */
    static boolean isBlankHtml(String html) {
        if (html == null || html.isBlank()) {
            return true;
        }
        String text = html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
        return text.isEmpty() && !html.matches("(?is).*<(img|video|audio)\\b.*");
    }

    private AdminDtos.WelcomeLetterDto toDto(WelcomeLetter letter) {
        if (letter == null) {
            return new AdminDtos.WelcomeLetterDto(false, "", "", null, null);
        }
        return new AdminDtos.WelcomeLetterDto(Boolean.TRUE.equals(letter.getEnabled()),
                letter.getTitle() == null ? "" : letter.getTitle(),
                letter.getContentHtml(), letter.getUpdatedBy(), letter.getUpdatedAt());
    }
}
