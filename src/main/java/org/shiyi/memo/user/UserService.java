package org.shiyi.memo.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String NICKNAME_TAKEN = "昵称已被使用，换一个吧";
    /** 邀请码用的字符集：去掉了 0/1/l/o 这些容易看错、念错的字符 */
    private static final String INVITE_ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final String DEFAULT_LEVEL_KEY = "default";

    private final AppUserMapper userMapper;
    private final SecureRandom random = new SecureRandom();
    private final UserLevelMapper levelMapper;
    private final FileAssetMapper fileAssetMapper;
    private final AppProperties properties;

    /**
     * 用户等级只在后台改，但上传、看配额时每次都要读一遍。
     * 缓存在进程内，后台改动会立刻失效；TTL 只是兜底（比如多实例部署时）。
     */
    private final Cache<Long, UserLevel> levelCache = Caffeine.newBuilder()
            .maximumSize(128)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /** 默认等级只有一行，单独存一个键 */
    private final Cache<String, UserLevel> defaultLevelCache = Caffeine.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    /**
     * 令牌版本只在改密码 / 封号时变化，没必要每个请求都查库。
     * 5 分钟的缓存意味着多实例部署时，其它实例最晚 5 分钟感知到吊销。
     */
    private final Cache<Long, Long> tokenVersionCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

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
        Long levelId = user.getLevelId();
        if (levelId == null) {
            return defaultLevel();
        }
        UserLevel cached = levelCache.getIfPresent(levelId);
        if (cached != null) {
            return cached;
        }
        UserLevel level = levelMapper.selectById(levelId);
        if (level == null) {
            return defaultLevel();
        }
        levelCache.put(levelId, level);
        return level;
    }

    public UserLevel defaultLevel() {
        UserLevel cached = defaultLevelCache.getIfPresent(DEFAULT_LEVEL_KEY);
        if (cached != null) {
            return cached;
        }
        UserLevel level = loadDefaultLevel();
        defaultLevelCache.put(DEFAULT_LEVEL_KEY, level);
        return level;
    }

    /** 等级被后台改动后调用：下一次读取会重新查库 */
    public void invalidateLevelCache() {
        levelCache.invalidateAll();
        defaultLevelCache.invalidateAll();
    }

    private UserLevel loadDefaultLevel() {
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

    /** 本次调用是不是建了新号；欢迎信只发给刚注册的人 */
    public record FoundOrCreated(AppUser user, boolean created) {
    }

    @Transactional
    public FoundOrCreated findOrCreateByEmail(String email) {
        String normalized = normalizeEmail(email);
        AppUser existing = findByEmail(normalized);
        if (existing != null) {
            return new FoundOrCreated(existing, false);
        }
        if (!properties.getSecurity().isAllowRegister()) {
            throw ApiException.forbidden("该邮箱尚未注册，当前站点未开放注册");
        }
        AppUser user = new AppUser();
        user.setEmail(normalized);
        user.setNickname(uniqueNicknameFromEmail(normalized));
        user.setInviteCode(uniqueInviteCode());
        user.setLevelId(defaultLevel().getId());
        user.setStatus(1);
        user.setStorageUsed(0L);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.insert(user);
        return new FoundOrCreated(user, true);
    }

    public void touchLastLogin(long userId) {
        AppUser patch = new AppUser();
        patch.setId(userId);
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);
    }

    /** 按邀请码找人（邀请链接落地页用）；找不到或码为空都返回 null */
    public AppUser findByInviteCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return userMapper.selectOne(Wrappers.lambdaQuery(AppUser.class)
                .eq(AppUser::getInviteCode, code.trim().toLowerCase(Locale.ROOT)));
    }

    /** 生成一个没被占用的邀请码；连撞 20 次基本不可能，兜底换成 16 位 */
    private String uniqueInviteCode() {
        for (int i = 0; i < 20; i++) {
            String candidate = randomInviteCode(10);
            Long count = userMapper.selectCount(Wrappers.lambdaQuery(AppUser.class)
                    .eq(AppUser::getInviteCode, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        return randomInviteCode(16);
    }

    private String randomInviteCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return code.toString();
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

    /** 写入 BCrypt 之后的密码哈希，同时吊销该用户已经签发的令牌；返回新的版本号 */
    public long updatePasswordHash(long userId, String hash) {
        userMapper.updatePasswordHashAndBumpVersion(userId, hash);
        tokenVersionCache.invalidate(userId);
        return tokenVersionOf(userId);
    }

    /** 用户当前的令牌版本；用户不存在返回 -1，任何令牌都对不上 */
    public long tokenVersionOf(long userId) {
        Long cached = tokenVersionCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        AppUser user = userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .select(AppUser::getTokenVersion)
                .eq(AppUser::getId, userId));
        long version = user == null || user.getTokenVersion() == null ? -1L : user.getTokenVersion();
        tokenVersionCache.put(userId, version);
        return version;
    }

    /** 让该用户的所有旧令牌立即失效（封号、退出所有设备） */
    public void bumpTokenVersion(long userId) {
        userMapper.bumpTokenVersion(userId);
        tokenVersionCache.invalidate(userId);
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
