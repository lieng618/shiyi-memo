package org.shiyi.memo.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;
    private String nickname;
    /** 头像对应的附件 id，可以为 null */
    private Long avatarFileId;
    private String passwordHash;
    private Long levelId;
    private Integer status;
    private Long storageUsed;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isEnabled() {
        return status == null || status == 1;
    }
}
