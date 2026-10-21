package org.shiyi.memo.memo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("memo_edit_lease")
public class MemoEditLease {

    @TableId
    private Long memoId;
    private Long userId;
    private String sessionId;
    private String clientLabel;
    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;
}
