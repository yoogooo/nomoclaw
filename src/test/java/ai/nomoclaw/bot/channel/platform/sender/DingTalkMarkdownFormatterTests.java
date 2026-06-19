package ai.nomoclaw.bot.channel.platform.sender;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DingTalkMarkdownFormatterTests {

    @Test
    void shouldExtractTitleFromFirstMeaningfulLine() {
        String markdown = """
                
                ## 发布总结
                
                正文内容
                """;

        Assertions.assertEquals("发布总结", DingTalkMarkdownFormatter.extractTitle(markdown));
    }

    @Test
    void shouldConvertMarkdownTableToBulletList() {
        String markdown = """
                | 模块 | 状态 |
                | --- | --- |
                | 登录 | 完成 |
                | 支付 | 处理中 |
                """;

        Assertions.assertEquals("""
                - 模块: 登录，状态: 完成
                - 模块: 支付，状态: 处理中
                """.trim(), DingTalkMarkdownFormatter.formatMarkdown(markdown));
    }

    @Test
    void shouldFallbackTitleWhenInputIsBlank() {
        Assertions.assertEquals("NomoClaw", DingTalkMarkdownFormatter.extractTitle("   "));
        Assertions.assertEquals("暂无内容", DingTalkMarkdownFormatter.formatMarkdown("   "));
    }
}
