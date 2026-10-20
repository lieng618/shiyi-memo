package org.shiyi.memo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务配置，全部对应 application.yml 里的 app.* 节点。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 站点对外地址，用于邮件正文里的链接 */
    private String baseUrl = "http://localhost:8080";

    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private Security security = new Security();
    private Desktop desktop = new Desktop();

    @Data
    public static class Jwt {
        /** HS256 密钥，长度至少 32 字节 */
        private String secret = "dev-only-secret-please-change-me-0123456789";
        /** 单张令牌有效期（小时）。开启滑动续期后，它相当于「闲置多久需要重新登录」，默认 30 天 */
        private long ttlHours = 720;
        /** 从登录算起的绝对上限（小时），不管怎么续期到期都要重新登录，默认 180 天 */
        private long absoluteMaxHours = 4320;
        /** 距上次签发超过这个时长才重新签一张，避免每个请求都写 Cookie，默认每天一次 */
        private long refreshIntervalHours = 24;
        /** 使用中自动续期；关掉就退回固定有效期 */
        private boolean sliding = true;
        private String cookieName = "MEMO_TOKEN";
        /** 挂上 HTTPS 之后改成 true */
        private boolean secureCookie = false;
    }

    @Data
    public static class Storage {
        private String root = "./data/uploads";
        private String thumbnailRoot = "./data/thumbnails";
        /** 缩略图长边像素 */
        private int thumbnailMaxSize = 480;
    }

    /**
     * 后台控制台（独立站点 /admin）自己的账号，和备忘录用户完全无关。
     */
    @Data
    public static class Admin {
        private String username = "admin";
        private String password = "change-me";
        private String cookieName = "MEMO_ADMIN_TOKEN";
        /** 后台登录有效期（小时），默认 12 小时 */
        private long ttlHours = 12;
    }

    @Data
    public static class Mail {
        /** 是否真的发邮件；false 时验证码写入日志并通过接口返回 */
        private boolean enabled = false;
        private String from = "";
        private int codeTtlMinutes = 10;
        /** 邮件未启用时是否把验证码放进接口响应（公网部署必须为 false） */
        private boolean exposeCode = true;
        /** 同一邮箱重发间隔（秒） */
        private int resendIntervalSeconds = 60;
        /** 同一邮箱每天最多请求多少次验证码 */
        private int dailyLimitPerEmail = 30;
    }

    @Data
    public static class Security {
        /** 是否允许新邮箱自助注册 */
        private boolean allowRegister = true;
        /** 新用户默认等级编码 */
        private String defaultLevelCode = "free";
        /** 允许跨域的前端地址 */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    /** 桌面端（Electron 外壳）的发布信息，和站点是两条独立的版本线 */
    @Data
    public static class Desktop {
        /** 安装包下载地址（Gitee 发行版的固定地址）；留空则网页不显示桌面端入口 */
        private String downloadUrl = "";
    }
}
