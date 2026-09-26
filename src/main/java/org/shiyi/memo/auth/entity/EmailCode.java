package org.shiyi.memo.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_code")
public class EmailCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;
    private String code;
    private String purpose;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Integer attempts;
    private String ip;
    private LocalDateTime createdAt;
}
