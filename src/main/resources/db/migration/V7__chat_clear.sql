-- 清空聊天记录：一条消息一行是双方共用的，所以清空只记「自己清到哪一刻」，
-- 查询时过滤掉这一刻之前的消息，对方的记录不受影响
CREATE TABLE chat_clear
(
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL COMMENT '归属方',
    friend_id  BIGINT   NOT NULL COMMENT '会话对方',
    cleared_at DATETIME NOT NULL COMMENT '此刻之前的消息对归属方不可见',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_clear_pair (user_id, friend_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '好友聊天清空标记';
