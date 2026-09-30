-- 好友关系：接受申请时写入两行，双方各一行，查询和删除都按 user_id 一刀切
CREATE TABLE friend
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL COMMENT '归属方',
    friend_id  BIGINT   NOT NULL COMMENT '对方',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '成为好友的时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_friend_pair (user_id, friend_id),
    KEY idx_friend_user (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '好友关系';

-- 好友聊天：一条消息一行，会话靠 sender/recipient 两个方向拼出来
CREATE TABLE chat_message
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sender_id    BIGINT        NOT NULL,
    recipient_id BIGINT        NOT NULL,
    content      VARCHAR(2000) NOT NULL,
    read_at      DATETIME      NULL COMMENT '收件人拉取会话的时间',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_pair (sender_id, recipient_id, id),
    KEY idx_chat_recipient (recipient_id, read_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '好友聊天';

-- 好友申请沿用站内信：type = FRIEND_REQUEST 的消息就是申请本身，
-- ref_user_id 记申请人，request_status 记处理进度
ALTER TABLE user_message
    ADD COLUMN ref_user_id    BIGINT      NULL COMMENT 'FRIEND_REQUEST 时的申请人' AFTER ref_file_id,
    ADD COLUMN request_status VARCHAR(16) NULL COMMENT 'FRIEND_REQUEST 的状态：PENDING / ACCEPTED / REJECTED' AFTER ref_user_id,
    ADD KEY idx_user_message_ref_user (ref_user_id);
