-- 首次建表：完整最终态 schema。历史增量迁移已合并删除（项目未上线，无需升级路径）。
-- memo.content_html 存 wangEditor 的富文本 HTML。
-- user_message.content_html 存后台富文本通知的 HTML，content 保留去标签后的纯文本（列表预览 / 旧数据兼容）。

-- 用户等级：决定总空间上限，可在后台页面调整
CREATE TABLE user_level
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)  NOT NULL COMMENT '等级编码，程序里引用',
    name        VARCHAR(64)  NOT NULL COMMENT '等级名称，后台展示',
    max_storage BIGINT       NOT NULL COMMENT '总空间上限（字节）',
    description VARCHAR(255) NULL,
    sort        INT          NOT NULL DEFAULT 0,
    is_default  TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为新用户默认等级',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_level_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户等级 / 配额';

CREATE TABLE app_user
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    email          VARCHAR(160) NOT NULL,
    nickname       VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(100) NULL COMMENT '预留：以后要加密码登录时使用',
    avatar_file_id BIGINT       NULL COMMENT '头像附件 id',
    level_id       BIGINT       NOT NULL,
    status         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
    storage_used   BIGINT       NOT NULL DEFAULT 0 COMMENT '已用空间（字节）',
    last_login_at  DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email),
    UNIQUE KEY uk_app_user_nickname (nickname),
    KEY idx_app_user_level (level_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';

-- 邮箱验证码（登录 / 注册共用一套流程）
CREATE TABLE email_code
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(160) NOT NULL,
    code       VARCHAR(16)  NOT NULL,
    purpose    VARCHAR(16)  NOT NULL DEFAULT 'LOGIN',
    expires_at DATETIME     NOT NULL,
    used_at    DATETIME     NULL,
    attempts   INT          NOT NULL DEFAULT 0 COMMENT '校验失败次数，超过阈值作废',
    ip         VARCHAR(64)  NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_email_code_email (email, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='邮箱验证码';

-- 备忘录
CREATE TABLE memo
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(255) NOT NULL DEFAULT '',
    content_html  MEDIUMTEXT   NULL COMMENT '富文本 HTML（wangEditor）',
    pinned        TINYINT(1)   NOT NULL DEFAULT 0,
    pinned_at     DATETIME     NULL COMMENT '置顶时间，用于多条置顶时排序',
    share_token   VARCHAR(64)  NULL COMMENT '公开分享令牌',
    share_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 表示分享链接有效',
    shared_at     DATETIME     NULL COMMENT '最近一次开启分享的时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_memo_share_token (share_token),
    KEY idx_memo_user_updated (user_id, updated_at),
    KEY idx_memo_user_pinned (user_id, pinned, pinned_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='备忘录';

CREATE TABLE tag
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(64) NOT NULL,
    color      VARCHAR(16) NOT NULL DEFAULT '#4f7cff',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_user_name (user_id, name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='标签';

CREATE TABLE memo_tag
(
    memo_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (memo_id, tag_id),
    KEY idx_memo_tag_tag (tag_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='备忘录-标签关联';

-- 附件（照片 / 视频 / 文件）：元数据入库，文件本体落磁盘
CREATE TABLE file_asset
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    memo_id       BIGINT       NULL COMMENT '可为空：先上传再关联到某条记录',
    original_name VARCHAR(255) NOT NULL,
    ext           VARCHAR(32)  NOT NULL DEFAULT '',
    mime          VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size          BIGINT       NOT NULL,
    kind          VARCHAR(16)  NOT NULL DEFAULT 'other' COMMENT 'image / video / audio / other',
    width         INT          NULL,
    height        INT          NULL,
    storage_path  VARCHAR(512) NOT NULL COMMENT '相对存储根目录的路径',
    content_hash  CHAR(64)     NULL COMMENT '文件内容 SHA-256，用于同账号去重',
    blocked       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1 表示已被后台屏蔽',
    blocked_reason VARCHAR(255) NULL,
    blocked_at    DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_file_asset_user (user_id, created_at),
    KEY idx_file_asset_memo (memo_id),
    KEY idx_file_asset_hash (user_id, content_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='附件元数据';

-- 站内信：后台发给用户的通知，用户只能看，不能删
CREATE TABLE user_message
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    title          VARCHAR(128) NOT NULL,
    content        TEXT         NOT NULL,
    content_html   MEDIUMTEXT   NULL COMMENT '后台富文本通知正文（wangEditor HTML），系统消息为 NULL',
    type           VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT 'NOTICE / FILE_BLOCKED / MEMO_REMOVED',
    ref_memo_id    BIGINT       NULL,
    ref_file_id    BIGINT       NULL,
    ref_user_id    BIGINT       NULL COMMENT '历史好友申请遗留字段',
    request_status VARCHAR(16)  NULL COMMENT '历史好友申请遗留字段：PENDING / ACCEPTED / REJECTED',
    read_at        DATETIME     NULL,
    sender_name    VARCHAR(64)  NOT NULL DEFAULT '管理员',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_message_user (user_id, read_at),
    KEY idx_user_message_created (user_id, created_at),
    KEY idx_user_message_ref_user (ref_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='站内信';

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

-- 好友申请：申请本身、状态和处理时间都记在自己的表里
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

-- 好友聊天：一条消息一行，会话靠 sender/recipient 两个方向拼出来
CREATE TABLE chat_message
(
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    sender_id    BIGINT        NOT NULL,
    recipient_id BIGINT        NOT NULL,
    content      VARCHAR(2000) NOT NULL,
    deliverable  TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '0 = 没送达，只有发送方可见',
    read_at      DATETIME      NULL COMMENT '收件人拉取会话的时间',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_pair (sender_id, recipient_id, id),
    KEY idx_chat_recipient (recipient_id, read_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT = '好友聊天';

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

-- 内置三个等级，之后可以在后台页面随意加减
INSERT INTO user_level (code, name, max_storage, description, sort, is_default)
VALUES ('free', '免费版', 2147483648, '总空间 2 GB', 10, 1),
       ('standard', '标准版', 21474836480, '总空间 20 GB', 20, 0),
       ('premium', '高级版', 214748364800, '总空间 200 GB', 30, 0);
