-- 个人中心：头像（指向 file_asset 里的一张图片）
ALTER TABLE app_user
    ADD COLUMN avatar_file_id BIGINT NULL COMMENT '头像附件 id' AFTER nickname;

-- 只保留总空间上限，去掉单文件上限（超大文件仍受服务器 multipart 限制保护）
ALTER TABLE user_level
    DROP COLUMN max_file_size;

UPDATE user_level
SET description = CONCAT('总空间 ', ROUND(max_storage / 1024 / 1024 / 1024, 0), ' GB');
