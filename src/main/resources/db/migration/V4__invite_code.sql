-- 邀请链接：每个用户一个固定邀请码，朋友打开 /j/{code} 就能直接申请加好友，
-- 不用先知道对方的昵称或邮箱。
ALTER TABLE app_user
    ADD COLUMN invite_code VARCHAR(16) NULL COMMENT '邀请码，用于生成邀请链接 /j/{code}' AFTER nickname;

-- 已有用户补一个随机码（每个用户 10 位十六进制，碰概率可忽略）
UPDATE app_user
SET invite_code = LOWER(SUBSTRING(SHA2(CONCAT(id, '-', RAND(), '-', UUID()), 256), 1, 10))
WHERE invite_code IS NULL;

-- MySQL 的唯一索引允许多个 NULL，新建用户由程序生成后再写入
ALTER TABLE app_user
    ADD UNIQUE KEY uk_app_user_invite_code (invite_code);
