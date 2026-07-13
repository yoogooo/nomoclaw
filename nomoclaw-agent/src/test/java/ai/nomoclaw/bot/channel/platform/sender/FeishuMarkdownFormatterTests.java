package ai.nomoclaw.bot.channel.platform.sender;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class FeishuMarkdownFormatterTests {

    @Test
    void shouldExtractTitleFromFirstMeaningfulLine() {
        String markdown = """
                
                ## 发布总结
                
                正文内容
                """;

        Assertions.assertEquals("【发布总结】", FeishuMarkdownFormatter.extractTitle(markdown));
    }

    @Test
    void shouldConvertMarkdownIntoFeishuPostContent() {
        String markdown = """
                ## 核心进展
                | 模块 | 状态 |
                | --- | --- |
                | 登录 | 完成 |
                访问 [控制台](https://example.com)
                """;

        Map<String, Object> post = FeishuMarkdownFormatter.formatPost(markdown);
        Map<?, ?> zhCn = (Map<?, ?>) post.get("zh_cn");
        Assertions.assertEquals("【核心进展】", zhCn.get("title"));
        List<?> content = (List<?>) zhCn.get("content");
        Assertions.assertEquals(3, content.size());
        Assertions.assertTrue(content.get(0).toString().contains("【核心进展】"));
        Assertions.assertTrue(content.get(1).toString().contains("• 模块: 登录，状态: 完成"));
        Assertions.assertTrue(content.get(2).toString().contains("访问 控制台 (https://example.com)"));
    }

    @Test
    void shouldFallbackWhenInputIsBlank() {
        Map<String, Object> post = FeishuMarkdownFormatter.formatPost("   ");
        Map<?, ?> zhCn = (Map<?, ?>) post.get("zh_cn");
        Assertions.assertEquals("NomoClaw", zhCn.get("title"));
        Assertions.assertTrue(zhCn.get("content").toString().contains("暂无内容"));
    }
}
