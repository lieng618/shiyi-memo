-- 公开分享：保留令牌，关闭后旧链接仍能明确返回“没有权限”
ALTER TABLE memo
    ADD COLUMN share_token VARCHAR(64) NULL COMMENT '公开分享令牌' AFTER pinned_at,
    ADD COLUMN share_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 表示分享链接有效' AFTER share_token,
    ADD COLUMN shared_at DATETIME NULL COMMENT '最近一次开启分享的时间' AFTER share_enabled,
    ADD UNIQUE KEY uk_memo_share_token (share_token);
