package org.shiyi.memo.common;

import java.util.List;
import java.util.function.Function;

/**
 * 统一的分页返回结构。
 */
public record PageResult<T>(List<T> records, long total, long page, long size, boolean hasMore) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size, (long) page * size < total);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(records.stream().map(mapper).toList(), total, page, size, hasMore);
    }
}
