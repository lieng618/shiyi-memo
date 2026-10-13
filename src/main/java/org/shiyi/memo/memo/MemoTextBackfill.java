package org.shiyi.memo.memo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shiyi.memo.memo.entity.Memo;
import org.shiyi.memo.memo.mapper.MemoMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史数据补课：content_text / excerpt 是 V5 迁移新加的列，
 * 存量笔记在第一次启动时按 id 分批补齐。只处理这两列还是 NULL 的行，
 * 补完之后每次启动就只剩一条空查询。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoTextBackfill implements ApplicationRunner {

    private static final int BATCH_SIZE = 200;

    private final MemoMapper memoMapper;

    @Override
    public void run(ApplicationArguments args) {
        long lastId = 0;
        int filled = 0;
        while (true) {
            List<Memo> batch = memoMapper.selectList(Wrappers.lambdaQuery(Memo.class)
                    // 只取正文，避免把要回填的那两列再读一遍
                    .select(Memo::getId, Memo::getContentHtml)
                    .isNull(Memo::getContentText)
                    .gt(Memo::getId, lastId)
                    .orderByAsc(Memo::getId)
                    .last("limit " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (Memo memo : batch) {
                Memo patch = new Memo();
                patch.setId(memo.getId());
                MemoService.fillDerivedText(memo, patch);
                memoMapper.updateById(patch);
                lastId = memo.getId();
                filled++;
            }
        }
        if (filled > 0) {
            log.info("已为 {} 条历史笔记补齐检索文本", filled);
        }
    }
}
