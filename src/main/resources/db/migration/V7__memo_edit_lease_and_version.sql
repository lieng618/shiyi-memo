-- 笔记正文版本：保存时做条件更新，避免不同设备互相覆盖。
ALTER TABLE memo
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '正文保存版本，每次成功保存 +1' AFTER content_html;

-- 同一篇笔记同一时间只允许一个前端会话编辑。
-- 租约独立成表，续租时不会改动 memo.updated_at，也不会让笔记在列表里反复跳到最前。
CREATE TABLE memo_edit_lease
(
    memo_id      BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    session_id   VARCHAR(64)  NOT NULL,
    client_label VARCHAR(128) NOT NULL DEFAULT '其他设备',
    acquired_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (memo_id),
    KEY idx_memo_edit_lease_user (user_id, expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='笔记编辑租约';
