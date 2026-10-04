-- 站内信富文本正文：后台发信像写笔记一样支持内嵌图片和附件链接（wangEditor HTML）。
-- content 继续保留一份去标签后的纯文本，供后台列表预览和旧数据兼容。
ALTER TABLE user_message ADD COLUMN content_html MEDIUMTEXT NULL AFTER content;
