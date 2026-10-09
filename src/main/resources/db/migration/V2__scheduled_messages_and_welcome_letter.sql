-- 站内信定时送达：send_at 为空表示立即送达。
-- 没到点的消息对收件人不可见（列表 / 未读角标 / 详情 / 附件授权都按它过滤），
-- 所以不需要后台定时任务，靠查询条件「到点自动出现」。
ALTER TABLE user_message
    ADD COLUMN send_at DATETIME NULL COMMENT '定时送达时间，NULL 表示立即送达' AFTER read_at;

-- 欢迎信：单行配置表，后台开启后新注册用户自动收到一封站内信
CREATE TABLE welcome_letter
(
    id           TINYINT      NOT NULL DEFAULT 1 COMMENT '固定为 1，全表只有一行',
    enabled      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '开启后新注册的用户会收到',
    title        VARCHAR(128) NOT NULL DEFAULT '' COMMENT '站内信标题',
    content_html MEDIUMTEXT   NULL COMMENT '正文（wangEditor HTML），支持 {nickname} / {email} 占位符',
    updated_by   VARCHAR(64)  NULL COMMENT '最后一次改动的后台账号',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='新用户欢迎信';

-- 预置一行草稿（默认关闭，等后台点开启），省得打开页面是一片空白
INSERT INTO welcome_letter (id, enabled, title, content_html)
VALUES (1, 0, '欢迎使用拾忆',
        '<p>你好 {nickname}，账号已经创建好了。</p><p>这里有三件可以马上做的事：</p><ol><li>写第一篇笔记，支持富文本、图片和附件</li><li>给笔记打标签，之后可以按标签筛选</li><li>想分享给别人时，点笔记右侧的分享按钮生成链接</li></ol><p>密码可以随时在「个人中心」设置或修改，也可以一直用邮箱验证码登录。</p><p>—— 拾忆</p>');
