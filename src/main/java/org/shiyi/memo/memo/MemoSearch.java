package org.shiyi.memo.memo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.shiyi.memo.memo.entity.Memo;

/**
 * 笔记关键词检索。
 *
 * <p>两个及以上字符（按码点算，中文一个字算一个）走 {@code (title, content_text)} 上的
 * ngram 全文索引；单个字没有对应的 bigram，退回 LIKE，行为跟以前一致。
 *
 * <p>MATCH() 里的列必须和索引定义完全一致，所以调用点不要拆开只匹配某一列。
 */
public final class MemoSearch {

    /** ngram 默认按 2 字切词，短于这个长度的关键词没有可用词元 */
    private static final int NGRAM_MIN_LENGTH = 2;

    private MemoSearch() {
    }

    public static void applyKeyword(LambdaQueryWrapper<Memo> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        if (kw.codePointCount(0, kw.length()) >= NGRAM_MIN_LENGTH) {
            wrapper.apply("MATCH(title, content_text) AGAINST ({0} IN BOOLEAN MODE)", phrase(kw));
            return;
        }
        wrapper.and(q -> q.like(Memo::getTitle, kw).or().like(Memo::getContentText, kw));
    }

    /**
     * 包成短语：ngram 下等价于子串匹配，同时用户输入里的
     * {@code + - * ~ ( ) "} 这些布尔操作符不会被当成语法。
     */
    private static String phrase(String keyword) {
        return "\"" + keyword.replace("\"", " ").replace("\\", " ") + "\"";
    }
}
