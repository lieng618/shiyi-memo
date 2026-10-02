package org.shiyi.memo.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.config.AppProperties;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.security.AuthUser;
import org.shiyi.memo.user.dto.UserDtos;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.entity.UserLevel;
import org.shiyi.memo.user.mapper.AppUserMapper;
import org.shiyi.memo.user.mapper.UserLevelMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String NICKNAME_TAKEN = "昵称已被使用，换一个吧";

    private final AppUserMapper userMapper;
    private final UserLevelMapper levelMapper;
    private final FileAssetMapper fileAssetMapper;
    private final AppProperties properties;

    public AppUser get(long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
        return user;
    }

    public AppUser requireEnabled(long id) {
        AppUser user = get(id);
        if (!user.isEnabled()) {
            throw ApiException.forbidden("账号已被禁用，请联系管理员");
        }
        return user;
    }

    public AppUser findByEmail(String email) {
        return userMapper.selectOne(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getEmail, normalizeEmail(email)));
    }

    public UserLevel levelOf(AppUser user) {
        UserLevel level = user.getLevelId() == null ? null : levelMapper.selectById(user.getLevelId());
        return level != null ? level : defaultLevel();
    }

    public UserLevel defaultLevel() {
        UserLevel level = levelMapper.selectOne(Wrappers.lambdaQuery(UserLevel.class)
                .eq(UserLevel::getIsDefault, true)
                .orderByAsc(UserLevel::getSort)
                .last("limit 1"));
        if (level == null) {
            level = levelMapper.selectOne(Wrappers.lambdaQuery(UserLevel.class)
                    .eq(UserLevel::getCode, properties.getSecurity().getDefaultLevelCode())
                    .last("limit 1"));
        }
        if (level == null) {
            throw new IllegalStateException("没有可用的用户等级，请检查 user_level 表");
        }
        return level;
    }

    public List<UserLevel> listLevels() {
        return levelMapper.selectList(Wrappers.lambdaQuery(UserLevel.class)
                .orderByAsc(UserLevel::getSort)
                .orderByAsc(UserLevel::getId));
    }

    public UserLevel getLevel(long levelId) {
        UserLevel level = levelMapper.selectById(levelId);
        if (level == null) {
            throw ApiException.badRequest("等级不存在");
        }
        return level;
    }

    public UserDtos.QuotaDto quotaOf(AppUser user) {
        UserLevel level = levelOf(user);
        long used = user.getStorageUsed() == null ? 0 : user.getStorageUsed();
        int percent = level.getMaxStorage() <= 0 ? 0
                : (int) Math.min(100, Math.round(used * 100.0 / level.getMaxStorage()));
        return new UserDtos.QuotaDto(level.getId(), level.getName(),
                level.getMaxStorage(), used, Math.max(0, level.getMaxStorage() - used), percent);
    }

    public void addStorageUsed(long userId, long delta) {
        userMapper.addStorageUsed(userId, delta);
    }

    @Transactional
    public AppUser findOrCreateByEmail(String email) {
        String normalized = normalizeEmail(email);
        AppUser existing = findByEmail(normalized);
        if (existing != null) {
            return existing;
        }
        if (!properties.getSecurity().isAllowRegister()) {
            throw ApiException.forbidden("该邮箱尚未注册，当前站点未开放注册");
        }
        AppUser user = new AppUser();
        user.setEmail(normalized);
        user.setNickname(uniqueNicknameFromEmail(normalized));
        user.setLevelId(defaultLevel().getId());
        user.setStatus(1);
        user.setStorageUsed(0L);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    public void touchLastLogin(long userId) {
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    public AppUser updateNickname(long userId, String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        ensureNicknameAvailable(value, userId);
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setNickname(value);
        try {
            userMapper.updateById(patch);
        } catch (DuplicateKeyException ex) {
            // 并发下唯一索引兜底，给用户看到和查重一样的提示
            throw ApiException.badRequest(NICKNAME_TAKEN);
        }
        return get(userId);
    }

    /**
     * 昵称唯一：改昵称、后台改昵称都先过这里；数据库上还有唯一索引兜底。
     * selfId 传自己的 id，避免把自己当成重名。
     */
    public void ensureNicknameAvailable(String nickname, Long selfId) {
        if (!StringUtils.hasText(nickname)) {
            throw ApiException.badRequest("昵称不能为空");
        }
        Long count = userMapper.selectCount(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getNickname, nickname)
                .ne(selfId != null, AppUser::getId, selfId));
        if (count != null && count > 0) {
            throw ApiException.badRequest(NICKNAME_TAKEN);
        }
    }

    /** 设置头像：只接受自己上传的图片；fileId 传 null 表示回到默认头像 */
    public AppUser updateAvatar(long userId, Long fileId) {
        if (fileId != null) {
            FileAsset asset = fileAssetMapper.selectById(fileId);
            if (asset == null || !asset.getUserId().equals(userId)) {
                throw ApiException.badRequest("头像文件不存在");
            }
            if (!"image".equals(asset.getKind())) {
                throw ApiException.badRequest("头像必须是图片");
            }
        }
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setAvatarFileId(fileId);
        userMapper.updateById(patch);
        return get(userId);
    }

    /** 写入 BCrypt 之后的密码哈希 */
    public void updatePasswordHash(long userId, String hash) {
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setPasswordHash(hash);
        userMapper.updateById(patch);
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String nicknameFromEmail(String email) {
        int at = email.indexOf('@');
        String name = at > 0 ? email.substring(0, at) : email;
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    /** 首次登录自动注册时的初始昵称，重名就往后加序号，保证唯一 */
    private String uniqueNicknameFromEmail(String email) {
        String base = nicknameFromEmail(email);
        String candidate = base;
        for (int i = 2; i <= 50; i++) {
            Long count = userMapper.selectCount(Wrappers.lambdaQuery(AppUser.class)
                    .eq(AppUser::getNickname, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = base + i;
        }
        return base + "_" + (System.currentTimeMillis() % 100000);
    }
}
