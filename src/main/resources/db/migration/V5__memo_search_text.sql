-- 搜索优化：原来是在 content_html 上做 LIKE '%关键字%'，
-- 既没法用索引（MEDIUMTEXT 全表扫），又会把标签名和属性值当正文命中
-- （搜 "div" / "class" 会命中几乎所有带富文本的笔记）。
--
-- 这里加两列派生数据：去标签后的纯文本 + 列表摘要，
-- 并给 (title, content_text) 建 ngram 全文索引——中文按 2 字一组切词，
-- 语义上等价于任意子串匹配，只是走了索引。
-- 存量数据由启动时的 MemoTextBackfill 补齐（只填 NULL 的行）。

ALTER TABLE memo
    ADD COLUMN content_text MEDIUMTEXT NULL COMMENT '去标签后的纯文本，供全文检索与摘要' AFTER content_html,
    ADD COLUMN excerpt VARCHAR(200) NULL COMMENT '列表摘要（纯文本前 140 字）' AFTER content_text,
    ADD FULLTEXT KEY ft_memo_search (title, content_text) WITH PARSER ngram;
