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
    /** 邀请码：拼成 /j/{code} 给朋友点，加了唯一索引；老数据由迁移补齐 */
    private String inviteCode;
    /** 头像对应的附件 id，可以为 null */
    private Long avatarFileId;
    private String passwordHash;
    /** 令牌版本：改密码、被禁用时 +1，已经签发的旧令牌随之失效 */
    private Long tokenVersion;
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
