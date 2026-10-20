-- 令牌版本：改密码、后台封号时 +1，已经签发的 JWT 随即失效，
-- 不用等服务端保存会话或等令牌自然过期。
ALTER TABLE app_user
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 COMMENT '令牌版本，变更后旧令牌立即失效' AFTER password_hash;
