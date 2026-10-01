-- 好友申请从站内信拆出来：申请本身、状态和处理时间都记在自己的表里
CREATE TABLE friend_request
(
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    from_user_id BIGINT      NOT NULL COMMENT '申请人',
    to_user_id   BIGINT      NOT NULL COMMENT '接收人',
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED',
    handled_at   DATETIME    NULL COMMENT '同意或拒绝的时间',
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_friend_request_incoming (to_user_id, status, created_at),
    KEY idx_friend_request_outgoing (from_user_id, status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '好友申请';

-- 把已经存在的好友申请迁过来，避免升级后待处理申请丢失
INSERT INTO friend_request (from_user_id, to_user_id, status, handled_at, created_at)
SELECT m.ref_user_id,
       m.user_id,
       COALESCE(m.request_status, 'PENDING'),
       CASE WHEN COALESCE(m.request_status, 'PENDING') = 'PENDING' THEN NULL ELSE m.created_at END,
       m.created_at
FROM user_message m
WHERE m.type = 'FRIEND_REQUEST'
  AND m.ref_user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1
                  FROM friend_request r
                  WHERE r.from_user_id = m.ref_user_id
                    AND r.to_user_id = m.user_id
                    AND r.created_at = m.created_at);
