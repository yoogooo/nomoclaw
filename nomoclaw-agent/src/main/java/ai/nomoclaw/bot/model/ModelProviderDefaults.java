package ai.nomoclaw.bot.model;

import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;

import java.util.List;

public final class ModelProviderDefaults {

    private ModelProviderDefaults() {
    }

    public static List<ModelConfigDto.Provider> providers() {
        return List.of(
                provider(
                        "codex",
                        "Codex",
                        "Codex Login",
                        false,
                        false,
                        true,
                        "https://chatgpt.com/backend-api/codex",
                        "",
                        "gpt-5.4",
                        List.of(
                                model("gpt-5.5", "5.5", List.of("text", "image"), true, 0, 0, 0, imageUpload()),
                                model("gpt-5.6-sol", "5.6 Sol", List.of("text", "image"), true, 0, 0, 0, imageUpload()),
                                model("gpt-5.6-terra", "5.6 Terra", List.of("text", "image"), true, 0, 0, 0, imageUpload()),
                                model("gpt-5.6-luna", "5.6 Luna", List.of("text", "image"), true, 0, 0, 0, imageUpload()),
                                model("gpt-5.4", "5.4", List.of("text", "image"), true, 0, 0, 0, imageUpload()),
                                model("gpt-5.4-mini", "5.4 Mini", List.of("text", "image"), true, 0, 0, 0, imageUpload())
                        )
                ),
                provider(
                        "deepseek",
                        "DeepSeek",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://api.deepseek.com",
                        "",
                        "deepseek-v4-flash",
                        List.of(
                                model("deepseek-v4-pro", "DeepSeek-V4-Pro", List.of("text"), true, 1048576, 0, 393216, disabledUpload()),
                                model("deepseek-v4-flash", "DeepSeek-V4-Flash", List.of("text"), true, 1048576, 0, 393216, disabledUpload())
                        )
                ),
                provider(
                        "dashscope",
                        "DashScope",
                        "OpenAI Compatible",
                        false,
                        true,
                        true,
                        "https://dashscope.aliyuncs.com/compatible-mode/v1",
                        "",
                        "qwen3.7-max",
                        List.of(
                                model("qwen3.7-max", "Qwen3.7 Max", List.of("text"), true, 1000000, 0, 65536, disabledUpload()),
                                model("qwen3-max", "Qwen3 Max", List.of("text"), true, 262144, 0, 0, disabledUpload()),
                                model("qwen3.5-plus", "Qwen3.5 Plus", List.of("text", "image", "video"), true, 1000000, 0, 65536,
                                        new ModelConfigDto.UploadPolicy(true, List.of("image", "video"), 64, 256, 0L, 0L, true, false)),
                                model("qwen3.6-plus", "Qwen3.6 Plus", List.of("text", "image", "video"), true, 1000000, 0, 65536,
                                        new ModelConfigDto.UploadPolicy(true, List.of("image", "video"), 64, 256, 0L, 0L, true, false)),
                                model("qwen3.7-plus", "Qwen3.7 Plus", List.of("text", "image", "video"), true, 1000000, 0, 65536,
                                        new ModelConfigDto.UploadPolicy(true, List.of("image", "video"), 64, 2048, 0L, 0L, true, false))
                        )
                ),
                provider(
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
                                        new ModelConfigDto.UploadPolicy(true, List.of("image", "pdf", "text"), 1, 5, 20971520L, 104857600L, true, false)),
                                model("qwen3.7-plus", "Qwen3.7 Plus", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("glm-5", "GLM-5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("glm-4.7", "GLM-4.7", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("MiniMax-M2.5", "MiniMax M2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("kimi-k2.5", "Kimi K2.5", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-max-2026-01-23", "Qwen3 Max 2026-01-23", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-coder-next", "Qwen3 Coder Next", List.of("text"), true, 0, 0, 0, disabledUpload()),
                                model("qwen3-coder-plus", "Qwen3 Coder Plus", List.of("text"), true, 0, 0, 0, disabledUpload())
                        )
                ),
                provider(
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
                provider(
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
                                model("gpt-5.3", "GPT-5.3", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gpt-5.4", "GPT-5.4", List.of("text", "image", "audio"), true, 0, 0, 0, disabledUpload()),
                                model("gpt-4.1", "GPT-4.1", List.of("text", "image"), false, 0, 0, 0, disabledUpload()),
                                model("gpt-4o", "GPT-4o", List.of("text", "image", "audio"), false, 0, 0, 0, disabledUpload())
                        )
                ),
                provider(
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
                provider(
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
                provider(
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

    private static ModelConfigDto.Provider provider(String id,
                                                   String name,
                                                   String protocol,
                                                   boolean local,
                                                   boolean requireApiKey,
                                                   boolean freezeUrl,
                                                   String baseUrl,
                                                   String apiKey,
                                                   String defaultModel,
                                                   List<ModelConfigDto.Model> models) {
        return new ModelConfigDto.Provider(
                id,
                name,
                protocol,
                local,
                requireApiKey,
                freezeUrl,
                baseUrl,
                apiKey,
                false,
                "missing",
                "",
                defaultModel,
                models
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
        return new ModelConfigDto.Model(id, name, capabilities, reasoning, contextWindow, maxInputTokens, maxOutputTokens, uploadPolicy, false, "builtin");
    }

    private static ModelConfigDto.UploadPolicy disabledUpload() {
        return new ModelConfigDto.UploadPolicy(false, List.of(), 0, 0, 0L, 0L, false, false);
    }

    private static ModelConfigDto.UploadPolicy imageUpload() {
        return new ModelConfigDto.UploadPolicy(true, List.of("image"), 0, 5, 20971520L, 104857600L, true, false);
    }
}
