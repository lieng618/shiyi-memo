-- 发送时对方已经把我删了：消息照样存下来，但只给发送方自己看，
-- 对方拉会话、未读数、未读预览都查不到这一行；关系恢复后重发会把标记改回 1
ALTER TABLE chat_message
    ADD COLUMN deliverable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0 = 没送达，只有发送方可见' AFTER content;
