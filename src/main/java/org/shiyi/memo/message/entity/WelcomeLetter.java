package org.shiyi.memo.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新用户欢迎信配置。单行表，id 固定为 1，后台改一行就等于改全站配置。
 */
@Data
@TableName("welcome_letter")
public class WelcomeLetter {

    @TableId(type = IdType.INPUT)
    private Integer id;

    /** 开启后新注册的用户才会收到 */
    private Boolean enabled;

    private String title;
    /** 正文 HTML，支持 {nickname} / {email} 占位符 */
    private String contentHtml;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
