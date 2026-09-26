-- 备忘录用户不再区分角色：所有账号都是普通用户，后台是独立站点
ALTER TABLE app_user
    DROP COLUMN role;

-- 违规文件屏蔽
ALTER TABLE file_asset
    ADD COLUMN blocked        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 表示已被后台屏蔽',
    ADD COLUMN blocked_reason VARCHAR(255) NULL,
    ADD COLUMN blocked_at     DATETIME     NULL;

-- 站内信：后台发给用户的通知，用户只能看，不能删
CREATE TABLE user_message
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    content      TEXT         NOT NULL,
    type         VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE / FILE_BLOCKED / MEMO_REMOVED',
    ref_memo_id  BIGINT       NULL,
    ref_file_id  BIGINT       NULL,
    read_at      DATETIME     NULL,
    sender_name  VARCHAR(64)  NOT NULL DEFAULT '管理员',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_message_user (user_id, read_at),
    KEY idx_user_message_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';
