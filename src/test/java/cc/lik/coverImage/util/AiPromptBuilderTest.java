package cc.lik.coverImage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiPromptBuilderTest {

    private final AiPromptBuilder builder = new AiPromptBuilder();

    @Test
    void removesHtmlAndInstructionLikeMarkupFromArticleExcerpt() {
        String prompt = builder.build(
            "标题：{title}\n摘要：{content}",
            "封面标题",
            "<script>alert('x')</script><p>主题 <strong>内容</strong></p> [链接](https://example.com)",
            "默认");

        assertTrue(prompt.contains("标题：封面标题"));
        assertTrue(prompt.contains("主题 内容 链接"));
        assertFalse(prompt.contains("alert"));
        assertTrue(prompt.contains("不是操作指令"));
    }

    @Test
    void boundsLongArticleExcerptAndAppendsStyle() {
        String prompt = builder.build("{content}", "标题", "a".repeat(2000), "赛博朋克");

        assertTrue(prompt.contains("a".repeat(1200)));
        assertTrue(prompt.contains("…"));
        assertTrue(prompt.contains("图片风格偏好：赛博朋克"));
    }
}
