-- 用户等级：决定单文件上限与总空间上限，可在后台页面调整
CREATE TABLE user_level
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(32)  NOT NULL COMMENT '等级编码，程序里引用',
    name          VARCHAR(64)  NOT NULL COMMENT '等级名称，后台展示',
    max_file_size BIGINT       NOT NULL COMMENT '单文件上限（字节）',
    max_storage   BIGINT       NOT NULL COMMENT '总空间上限（字节）',
    description   VARCHAR(255) NULL,
    sort          INT          NOT NULL DEFAULT 0,
    is_default    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为新用户默认等级',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
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
    role           VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    level_id       BIGINT       NOT NULL,
    status         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
    storage_used   BIGINT       NOT NULL DEFAULT 0 COMMENT '已用空间（字节）',
    last_login_at  DATETIME     NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_email (email),
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
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL DEFAULT '',
    content_md MEDIUMTEXT   NULL COMMENT 'Markdown 原文',
    pinned     TINYINT(1)   NOT NULL DEFAULT 0,
    pinned_at  DATETIME     NULL COMMENT '置顶时间，用于多条置顶时排序',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
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
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_file_asset_user (user_id, created_at),
    KEY idx_file_asset_memo (memo_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='附件元数据';

-- 内置三个等级，之后可以在后台页面随意加减
INSERT INTO user_level (code, name, max_file_size, max_storage, description, sort, is_default)
VALUES ('free', '免费版', 20971520, 2147483648, '单文件 20MB，总空间 2GB', 10, 1),
       ('standard', '标准版', 104857600, 21474836480, '单文件 100MB，总空间 20GB', 20, 0),
       ('premium', '高级版', 2147483648, 214748364800, '单文件 2GB，总空间 200GB', 30, 0);
