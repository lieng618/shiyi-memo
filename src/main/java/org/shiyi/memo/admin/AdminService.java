package org.shiyi.memo.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.shiyi.memo.admin.dto.AdminDtos;
import org.shiyi.memo.auth.EmailCodeService;
import org.shiyi.memo.auth.entity.EmailCode;
import org.shiyi.memo.auth.mapper.EmailCodeMapper;
import org.shiyi.memo.common.ApiException;
import org.shiyi.memo.common.PageResult;
import org.shiyi.memo.file.FileService;
import org.shiyi.memo.file.dto.FileDtos;
import org.shiyi.memo.file.entity.FileAsset;
import org.shiyi.memo.file.mapper.FileAssetMapper;
import org.shiyi.memo.memo.MemoService;
import org.shiyi.memo.memo.MemoSearch;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.shiyi.memo.message.MessageService;
import org.shiyi.memo.message.WelcomeLetterService;
import org.shiyi.memo.message.entity.UserMessage;
import org.shiyi.memo.message.mapper.UserMessageMapper;
import org.shiyi.memo.tag.mapper.TagMapper;
import org.shiyi.memo.user.UserService;
import org.shiyi.memo.user.dto.UserDtos;
import org.shiyi.memo.user.entity.AppUser;
import org.shiyi.memo.user.entity.UserLevel;
import org.shiyi.memo.user.mapper.AppUserMapper;
import org.shiyi.memo.user.mapper.UserLevelMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 后台控制台（独立站点 /admin）的业务逻辑。
 * 它有自己的登录态，和备忘录用户体系完全无关。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final String CODE_STATUS_VALID = "VALID";
    private static final String CODE_STATUS_EXPIRED = "EXPIRED";
    private static final String CODE_STATUS_USED = "USED";
    private static final String CODE_STATUS_BLOCKED = "BLOCKED";

    private final AppUserMapper userMapper;
    private final UserLevelMapper levelMapper;
    private final MemoMapper memoMapper;
    private final FileAssetMapper fileMapper;
    private final UserMessageMapper messageMapper;
    private final TagMapper tagMapper;
    private final EmailCodeMapper emailCodeMapper;
    private final UserService userService;
    private final FileService fileService;
    private final MessageService messageService;
    private final WelcomeLetterService welcomeLetterService;

    // ---------------- 概览 ----------------

    public AdminDtos.StatsDto stats() {
        long storage = userMapper.selectList(Wrappers.lambdaQuery(AppUser.class)).stream()
                .map(AppUser::getStorageUsed)
                .filter(Objects::nonNull)
                .reduce(0L, Long::sum);
        return new AdminDtos.StatsDto(
                userMapper.selectCount(null),
                memoMapper.selectCount(null),
                fileMapper.selectCount(null),
                fileMapper.selectCount(Wrappers.lambdaQuery(FileAsset.class).eq(FileAsset::getBlocked, true)),
                storage,
                messageMapper.selectCount(Wrappers.lambdaQuery(UserMessage.class)
                        .ne(UserMessage::getType, "FRIEND_REQUEST")),
                messageMapper.selectCount(Wrappers.lambdaQuery(UserMessage.class)
                        .ne(UserMessage::getType, "FRIEND_REQUEST")
                        .isNull(UserMessage::getReadAt)));
    }

    // ---------------- 用户 ----------------

    public PageResult<AdminDtos.AdminUserDto> listUsers(String keyword, Integer status, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(AppUser.class);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(q -> q.like(AppUser::getEmail, kw).or().like(AppUser::getNickname, kw));
        }
        if (status != null) {
            wrapper.eq(AppUser::getStatus, status);
        }
        wrapper.orderByDesc(AppUser::getId);

        IPage<AppUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        List<AppUser> users = result.getRecords();
        if (users.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), page, size);
        }
        List<Long> ids = users.stream().map(AppUser::getId).toList();
        Map<Long, Long> memoCounts = toCountMap(memoMapper.countByUserIds(ids));
        Map<Long, Long> fileCounts = toCountMap(fileMapper.countByUserIds(ids, false));
        Map<Long, Long> blockedCounts = toCountMap(fileMapper.countByUserIds(ids, true));
        Map<Long, Long> messageCounts = toCountMap(messageMapper.countByUserIds(ids));
        Map<Long, UserLevel> levels = levelMap();

        List<AdminDtos.AdminUserDto> records = users.stream()
                .map(user -> {
                    UserLevel level = levels.get(user.getLevelId());
                    return new AdminDtos.AdminUserDto(
                            user.getId(), user.getEmail(), user.getNickname(), avatarUrl(user),
                            user.getStatus(),
                            user.getLevelId(), level == null ? null : level.getName(),
                            level == null ? null : level.getMaxStorage(), user.getStorageUsed(),
                            memoCounts.getOrDefault(user.getId(), 0L),
                            fileCounts.getOrDefault(user.getId(), 0L),
                            blockedCounts.getOrDefault(user.getId(), 0L),
                            messageCounts.getOrDefault(user.getId(), 0L),
                            user.getLastLoginAt(), user.getCreatedAt());
                })
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Transactional
    public AdminDtos.AdminUserDto updateUser(long userId, AdminDtos.UpdateUserRequest request) {
        AppUser user = userService.get(userId);
        AppUser patch = new AppUser();
        patch.setId(user.getId());
        if (StringUtils.hasText(request.nickname())) {
            String nickname = request.nickname().trim();
            // 昵称唯一，后台改也不能和别人撞
            userService.ensureNicknameAvailable(nickname, user.getId());
            patch.setNickname(nickname);
        }
        if (request.levelId() != null) {
            patch.setLevelId(userService.getLevel(request.levelId()).getId());
        }
        if (request.status() != null) {
            patch.setStatus(request.status() == 0 ? 0 : 1);
        }
        try {
            userMapper.updateById(patch);
        } catch (DuplicateKeyException ex) {
            throw ApiException.badRequest("昵称已被使用，换一个吧");
        }
        AppUser updated = userService.get(userId);
        UserLevel level = levelMap().get(updated.getLevelId());
        return new AdminDtos.AdminUserDto(updated.getId(), updated.getEmail(), updated.getNickname(),
                avatarUrl(updated), updated.getStatus(), updated.getLevelId(),
                level == null ? null : level.getName(),
                level == null ? null : level.getMaxStorage(), updated.getStorageUsed(),
                null, null, null, null, updated.getLastLoginAt(), updated.getCreatedAt());
    }

    public List<UserDtos.LevelDto> listLevels() {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : userMapper.countByLevel()) {
            counts.put(((Number) row.get("levelId")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return userService.listLevels().stream()
                .map(level -> UserDtos.LevelDto.from(level, counts.getOrDefault(level.getId(), 0L)))
                .toList();
    }

    @Transactional
    public UserDtos.LevelDto createLevel(UserDtos.LevelRequest request) {
        UserLevel level = new UserLevel();
        level.setCode(StringUtils.hasText(request.code())
                ? request.code().trim()
                : "level-" + System.currentTimeMillis());
        level.setName(request.name().trim());
        level.setMaxStorage(request.maxStorage());
        level.setDescription(request.description());
        level.setSort(request.sort() == null ? 100 : request.sort());
        level.setIsDefault(false);
        levelMapper.insert(level);
        if (Boolean.TRUE.equals(request.isDefault())) {
            markDefault(level.getId());
        }
        userService.invalidateLevelCache();
        return UserDtos.LevelDto.from(userService.getLevel(level.getId()), 0L);
    }

    @Transactional
    public UserDtos.LevelDto updateLevel(long levelId, UserDtos.LevelRequest request) {
        UserLevel level = userService.getLevel(levelId);
        UserLevel patch = new UserLevel();
        patch.setId(level.getId());
        if (StringUtils.hasText(request.code())) {
            patch.setCode(request.code().trim());
        }
        patch.setName(request.name().trim());
        patch.setMaxStorage(request.maxStorage());
        patch.setDescription(request.description());
        if (request.sort() != null) {
            patch.setSort(request.sort());
        }
        levelMapper.updateById(patch);
        if (Boolean.TRUE.equals(request.isDefault())) {
            markDefault(level.getId());
        }
        userService.invalidateLevelCache();
        return UserDtos.LevelDto.from(userService.getLevel(levelId), null);
    }

    @Transactional
    public void deleteLevel(long levelId) {
        UserLevel level = userService.getLevel(levelId);
        Long users = userMapper.selectCount(Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getLevelId, levelId));
        if (users != null && users > 0) {
            throw ApiException.badRequest("还有 " + users + " 个用户属于「" + level.getName() + "」，请先调整他们的等级");
        }
        List<UserLevel> levels = userService.listLevels();
        if (levels.size() <= 1) {
            throw ApiException.badRequest("至少要保留一个等级");
        }
        levelMapper.deleteById(levelId);
        if (Boolean.TRUE.equals(level.getIsDefault())) {
            markDefault(levels.stream().filter(item -> !item.getId().equals(levelId))
                    .findFirst().orElseThrow().getId());
        }
        userService.invalidateLevelCache();
    }

    // ---------------- 笔记 ----------------

    public PageResult<AdminDtos.AdminMemoDto> listMemos(String keyword, Long userId, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(Memo.class);
        MemoSearch.applyKeyword(wrapper, keyword);
        wrapper.eq(userId != null, Memo::getUserId, userId).orderByDesc(Memo::getId);

        IPage<Memo> result = memoMapper.selectPage(new Page<>(page, size), wrapper);
        List<Memo> memos = result.getRecords();
        if (memos.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), page, size);
        }
        Map<Long, AppUser> owners = usersOf(memos.stream().map(Memo::getUserId).toList());
        Map<Long, Long> fileCounts = toCountMapByKey(
                fileMapper.countByMemoIds(memos.stream().map(Memo::getId).toList()), "memoId");
        List<AdminDtos.AdminMemoDto> records = memos.stream()
                .map(memo -> {
                    AppUser owner = owners.get(memo.getUserId());
                    return new AdminDtos.AdminMemoDto(memo.getId(), memo.getUserId(),
                            owner == null ? null : owner.getEmail(),
                            owner == null ? null : owner.getNickname(),
                            memo.getTitle(), MemoService.summaryExcerpt(memo),
                            Boolean.TRUE.equals(memo.getPinned()),
                            fileCounts.getOrDefault(memo.getId(), 0L),
                            memo.getCreatedAt(), memo.getUpdatedAt());
                })
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public AdminDtos.AdminMemoDetail memoDetail(long memoId) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null) {
            throw ApiException.notFound("笔记不存在或已删除");
        }
        AppUser owner = userMapper.selectById(memo.getUserId());
        List<FileDtos.FileDto> files = fileService.listByMemo(memo.getUserId(), memoId);
        return new AdminDtos.AdminMemoDetail(memo.getId(), memo.getUserId(),
                owner == null ? null : owner.getEmail(),
                owner == null ? null : owner.getNickname(),
                memo.getTitle(), memo.getContentHtml(), files, memo.getCreatedAt(), memo.getUpdatedAt());
    }

    /** 删除违规笔记，并给作者发一封站内信说明 */
    @Transactional
    public void deleteMemo(long memoId, String adminName, String reason) {
        Memo memo = memoMapper.selectById(memoId);
        if (memo == null) {
            throw ApiException.notFound("笔记不存在或已删除");
        }
        String title = memo.getTitle() == null || memo.getTitle().isBlank() ? "无标题" : memo.getTitle();
        fileService.deleteByMemo(memo.getUserId(), memoId);
        tagMapper.unlinkAll(memoId);
        memoMapper.deleteById(memoId);
        messageService.send(memo.getUserId(), "你的笔记已被删除",
                "笔记「" + title + "」因违反站点规定已被管理员删除。"
                        + (StringUtils.hasText(reason) ? "\n\n说明：" + reason : ""),
                MessageService.TYPE_MEMO_REMOVED, null, null, adminName);
    }

    // ---------------- 文件 ----------------

    public PageResult<AdminDtos.AdminFileDto> listFiles(String keyword, Long userId, Boolean blocked, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(FileAsset.class);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileAsset::getOriginalName, keyword.trim());
        }
        wrapper.eq(userId != null, FileAsset::getUserId, userId);
        wrapper.eq(blocked != null, FileAsset::getBlocked, blocked);
        wrapper.orderByDesc(FileAsset::getId);

        IPage<FileAsset> result = fileMapper.selectPage(new Page<>(page, size), wrapper);
        List<FileAsset> files = result.getRecords();
        if (files.isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), page, size);
        }
        Map<Long, AppUser> owners = usersOf(files.stream().map(FileAsset::getUserId).toList());
        List<AdminDtos.AdminFileDto> records = files.stream()
                .map(asset -> toAdminFileDto(asset, owners.get(asset.getUserId())))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /** 屏蔽文件 + 给用户发站内信 */
    @Transactional
    public AdminDtos.AdminFileDto blockFile(long fileId, String reason, String adminName) {
        FileAsset asset = fileService.block(fileId, reason);
        messageService.send(asset.getUserId(), "你的文件已被屏蔽",
                "文件「" + asset.getOriginalName() + "」被管理员判定为违规内容，已屏蔽，你暂时无法查看或下载。"
                        + (StringUtils.hasText(reason) ? "\n\n原因：" + reason : ""),
                MessageService.TYPE_FILE_BLOCKED, asset.getMemoId(), asset.getId(), adminName);
        return toAdminFileDto(asset, userMapper.selectById(asset.getUserId()));
    }

    @Transactional
    public AdminDtos.AdminFileDto unblockFile(long fileId) {
        FileAsset asset = fileService.unblock(fileId);
        return toAdminFileDto(asset, userMapper.selectById(asset.getUserId()));
    }

    // ---------------- 站内信 ----------------

    @Transactional
    public void sendMessage(AdminDtos.SendMessageRequest request, String adminName) {
        userService.get(request.userId());
        messageService.sendRich(request.userId(), request.title().trim(), request.contentHtml(), adminName,
                request.sendAt());
    }

    /**
     * 全服信：全体正常状态用户每人一条（各自独立的已读状态，未读角标随之 +1）。
     * 用户量级是个人的备忘录应用，事务内逐条插入即可；将来量大了再换批量 INSERT。
     * 定了发送时间的话，信先落库但不显示，到点自动出现在各人的站内信里；
     * 收件人名单按「发送这一刻」的用户算，之后新注册的人不会补收。
     */
    @Transactional
    public void broadcast(AdminDtos.BroadcastMessageRequest request, String adminName) {
        List<Long> userIds = userMapper.selectList(Wrappers.lambdaQuery(AppUser.class)
                        .select(AppUser::getId)
                        .and(w -> w.eq(AppUser::getStatus, 1).or().isNull(AppUser::getStatus)))
                .stream().map(AppUser::getId).toList();
        for (Long userId : userIds) {
            messageService.sendRich(userId, request.title().trim(), request.contentHtml(), adminName,
                    request.sendAt());
        }
    }

    // ---------------- 欢迎信 ----------------

    public AdminDtos.WelcomeLetterDto welcomeLetter() {
        return welcomeLetterService.get();
    }

    public AdminDtos.WelcomeLetterDto updateWelcomeLetter(AdminDtos.WelcomeLetterRequest request,
                                                          String adminName) {
        return welcomeLetterService.update(request, adminName);
    }

    public PageResult<AdminDtos.AdminMessageDto> listMessages(Long userId, long page, long size) {
        var wrapper = Wrappers.lambdaQuery(UserMessage.class);
        wrapper.ne(UserMessage::getType, "FRIEND_REQUEST")
                .eq(userId != null, UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getId);
        IPage<UserMessage> result = messageMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserMessage> messages = result.getRecords();
        Map<Long, AppUser> users = messages.isEmpty()
                ? Map.of()
                : usersOf(messages.stream().map(UserMessage::getUserId).toList());
        List<AdminDtos.AdminMessageDto> records = messages.stream()
                .map(message -> {
                    AppUser owner = users.get(message.getUserId());
                    return new AdminDtos.AdminMessageDto(message.getId(), message.getUserId(),
                            owner == null ? null : owner.getEmail(),
                            owner == null ? null : owner.getNickname(),
                            message.getTitle(), message.getContent(), message.getType(), message.getSenderName(),
                            message.getSendAt(), message.getReadAt(), message.getCreatedAt());
                })
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    // ---------------- 验证码 ----------------

    /**
     * 邮箱验证码列表（只读）。排查「收不到验证码」时，管理员能看到当前还有效的明文验证码
     * 和它属于哪个邮箱。状态的判定顺序和 {@link EmailCodeService#verifyAndConsume} 保持一致：
     * 已使用 → 已过期 → 错误过多 → 有效。
     */
    public PageResult<AdminDtos.AdminEmailCodeDto> listEmailCodes(String email, String status,
                                                                  long page, long size) {
        LocalDateTime now = LocalDateTime.now();
        var wrapper = Wrappers.lambdaQuery(EmailCode.class);
        if (StringUtils.hasText(email)) {
            wrapper.like(EmailCode::getEmail, email.trim());
        }
        applyCodeStatusFilter(wrapper, status, now);
        wrapper.orderByDesc(EmailCode::getId);

        IPage<EmailCode> result = emailCodeMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminDtos.AdminEmailCodeDto> records = result.getRecords().stream()
                .map(code -> toAdminEmailCodeDto(code, now))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    private static AdminDtos.AdminEmailCodeDto toAdminEmailCodeDto(EmailCode code, LocalDateTime now) {
        String status;
        Long remainingSeconds = null;
        if (code.getUsedAt() != null) {
            status = CODE_STATUS_USED;
        } else if (code.getExpiresAt() == null || !code.getExpiresAt().isAfter(now)) {
            status = CODE_STATUS_EXPIRED;
        } else {
            remainingSeconds = Duration.between(now, code.getExpiresAt()).getSeconds();
            status = code.getAttempts() != null && code.getAttempts() >= EmailCodeService.MAX_ATTEMPTS
                    ? CODE_STATUS_BLOCKED : CODE_STATUS_VALID;
        }
        return new AdminDtos.AdminEmailCodeDto(code.getId(), code.getEmail(), code.getCode(), status,
                code.getExpiresAt(), code.getUsedAt(), code.getAttempts(), code.getIp(),
                code.getCreatedAt(), remainingSeconds);
    }

    /** 四个筛选条件互斥，和上面的状态判定一一对应；未知值不过滤（前端传 all 走这里） */
    private static void applyCodeStatusFilter(LambdaQueryWrapper<EmailCode> wrapper, String status,
                                              LocalDateTime now) {
        if (!StringUtils.hasText(status)) {
            return;
        }
        switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "valid" -> wrapper.isNull(EmailCode::getUsedAt)
                    .gt(EmailCode::getExpiresAt, now)
                    .and(q -> q.isNull(EmailCode::getAttempts)
                            .or().lt(EmailCode::getAttempts, EmailCodeService.MAX_ATTEMPTS));
            case "used" -> wrapper.isNotNull(EmailCode::getUsedAt);
            case "expired" -> wrapper.isNull(EmailCode::getUsedAt)
                    .le(EmailCode::getExpiresAt, now);
            case "blocked" -> wrapper.isNull(EmailCode::getUsedAt)
                    .gt(EmailCode::getExpiresAt, now)
                    .ge(EmailCode::getAttempts, EmailCodeService.MAX_ATTEMPTS);
            default -> {
                // 识别不了的状态不过滤，免得页面空白
            }
        }
    }

    // ---------------- 私有工具 ----------------

    private String avatarUrl(AppUser user) {
        return user.getAvatarFileId() == null ? null : "/api/files/" + user.getAvatarFileId() + "/thumb?w=160";
    }

    private AdminDtos.AdminFileDto toAdminFileDto(FileAsset asset, AppUser owner) {
        return new AdminDtos.AdminFileDto(asset.getId(), asset.getUserId(), owner == null ? null : owner.getEmail(),
                asset.getMemoId(), asset.getOriginalName(), asset.getKind(), asset.getMime(), asset.getSize(),
                asset.getBlocked(), asset.getBlockedReason(), asset.getBlockedAt(), asset.getCreatedAt());
    }

    private Map<Long, AppUser> usersOf(List<Long> userIds) {
        Map<Long, AppUser> map = new LinkedHashMap<>();
        List<Long> distinct = userIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        for (AppUser user : userMapper.selectBatchIds(distinct)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private Map<Long, UserLevel> levelMap() {
        Map<Long, UserLevel> map = new LinkedHashMap<>();
        for (UserLevel level : userService.listLevels()) {
            map.put(level.getId(), level);
        }
        return map;
    }

    private static Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        return toCountMapByKey(rows, "userId");
    }

    private static Map<Long, Long> toCountMapByKey(List<Map<String, Object>> rows, String key) {
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(((Number) row.get(key)).longValue(), ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    private void markDefault(long levelId) {
        UserLevel clear = new UserLevel();
        clear.setIsDefault(false);
        levelMapper.update(clear, Wrappers.lambdaUpdate(UserLevel.class).eq(UserLevel::getIsDefault, true));
        UserLevel set = new UserLevel();
        set.setIsDefault(true);
        levelMapper.update(set, Wrappers.lambdaUpdate(UserLevel.class).eq(UserLevel::getId, levelId));
    }
}
