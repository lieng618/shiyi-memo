-- 文件内容去重：记录 SHA-256，同一用户重复上传相同内容时复用磁盘上的物理文件，
-- 只建新的 asset 行（各行的备注归属、文件名、删除互不影响），不再多存一份
ALTER TABLE file_asset
    ADD COLUMN content_hash CHAR(64) NULL COMMENT '文件内容 SHA-256，用于同账号去重' AFTER storage_path,
    ADD KEY idx_file_asset_hash (user_id, content_hash);
