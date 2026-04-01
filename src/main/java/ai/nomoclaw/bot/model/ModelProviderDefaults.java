package ai.nomoclaw.bot.model;

import ai.nomoclaw.bot.application.dto.ModelConfigDto;

import java.util.List;

public final class ModelProviderDefaults {

    private ModelProviderDefaults() {
    }

    public static List<ModelConfigDto.Provider> providers() {
        return List.of(
                new ModelConfigDto.Provider(
                        "dashscope",
                        "DashScope",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "",
                        "qwen3-max",
                        List.of(
                                model("qwen3-max", "Qwen3 Max", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-235b-a22b-thinking-2507", "Qwen3 235B A22B Thinking", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("deepseek-v3.2", "DeepSeek-V3.2", List.of("text"), true, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "aliyun-codingplan",
                        "Aliyun Coding Plan",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://coding.dashscope.aliyuncs.com/v1",
                        "",
                        "qwen3.5-plus",
                        List.of(
                                model("qwen3.5-plus", "Qwen3.5 Plus", List.of("text"), true, 0, 0, 0,
                                        new ModelConfigDto.UploadPolicy(true, List.of("image", "pdf", "text"), 1, 5, true, false)),
                                model("glm-5", "GLM-5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("glm-4.7", "GLM-4.7", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("MiniMax-M2.5", "MiniMax M2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2.5", "Kimi K2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-max-2026-01-23", "Qwen3 Max 2026-01-23", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-coder-next", "Qwen3 Coder Next", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-coder-plus", "Qwen3 Coder Plus", List.of("text"), true, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "kimi-cn",
                        "Kimi",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://api.moonshot.cn/v1",
                        "",
                        "kimi-k2.5",
                        List.of(
                                model("kimi-k2.5", "Kimi K2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2-0905-preview", "Kimi K2 0905 Preview", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2-0711-preview", "Kimi K2 0711 Preview", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2-turbo-preview", "Kimi K2 Turbo Preview", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2-thinking", "Kimi K2 Thinking", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2-thinking-turbo", "Kimi K2 Thinking Turbo", List.of("text"), true, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "openai",
                        "OpenAI",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://api.openai.com/v1",
                        "",
                        "gpt-5.4",
                        List.of(
                                model("gpt-5.2", "GPT-5.2", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gpt-5.3", "GPT-5.3", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gpt-5.4", "GPT-5.4", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gpt-4.1", "GPT-4.1", List.of("text", "image"), false, 0, 0, 0, disabledUpload()),
                                model("gpt-4o", "GPT-4o", List.of("text", "image", "audio"), false, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "gemini",
                        "Google Gemini",
                        "Gemini Native",
                        false,
                        true,
                        true,
                        "https://generativelanguage.googleapis.com",
                        "",
                        "gemini-2.5-pro",
                        List.of(
                                model("gemini-2.5-pro", "Gemini 2.5 Pro", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gemini-2.5-flash", "Gemini 2.5 Flash", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gemini-2.0-flash", "Gemini 2.0 Flash", List.of("text", "image"), false, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "minimax-cn",
                        "MiniMax",
                        "Anthropic Compatible",
                        false,
                        true,
                        true,
                        "https://api.minimaxi.com/anthropic",
                        "",
                        "MiniMax-M2.5",
                        List.of(
                                model("MiniMax-M2.5", "MiniMax M2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("MiniMax-M2.5-highspeed", "MiniMax M2.5 Highspeed", List.of("text"), false, 0, 0, 0, disabledUpload()),
                                model("MiniMax-M2.7", "MiniMax M2.7", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("MiniMax-M2.7-highspeed", "MiniMax M2.7 Highspeed", List.of("text"), false, 0, 0, 0, disabledUpload())
                        )
                ),
                new ModelConfigDto.Provider(
                        "ollama",
                        "Ollama",
                        "Local Service",
                        true,
                        false,
                        false,
                        "http://127.0.0.1:11434",
                        "",
                        "",
                        List.of()
                )
        );
    }

    private static ModelConfigDto.Model model(String id,
                                              String name,
                                              List<String> capabilities,
                                              boolean reasoning,
                                              int contextWindow,
                                              int maxInputTokens,
                                              int maxOutputTokens,
                                              ModelConfigDto.UploadPolicy uploadPolicy) {
        return new ModelConfigDto.Model(id, name, capabilities, reasoning, contextWindow, maxInputTokens, maxOutputTokens, uploadPolicy);
    }

    private static ModelConfigDto.UploadPolicy disabledUpload() {
        return new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, false, false);
    }
}
