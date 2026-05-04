package ai.nomoclaw.bot.channel.model;

import java.util.Locale;

public enum ChannelType {
    WEB,
    FEISHU,
    DINGTALK,
    DISCORD,
    TELEGRAM,
    QQ,
    WECOM,
    WEIXIN,
    NOOP;

    public static ChannelType from(String value) {
        if (value == null || value.isBlank()) {
            return WEB;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "feishu", "lark" -> FEISHU;
            case "dingtalk", "ding" -> DINGTALK;
            case "discord" -> DISCORD;
            case "telegram", "tg" -> TELEGRAM;
            case "qq", "qqbot" -> QQ;
            case "wecom", "wechatwork", "qiyeweixin" -> WECOM;
            case "weixin", "wechat", "wechatmp" -> WEIXIN;
            case "noop" -> NOOP;
            case "web" -> WEB;
            default -> WEB;
        };
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
