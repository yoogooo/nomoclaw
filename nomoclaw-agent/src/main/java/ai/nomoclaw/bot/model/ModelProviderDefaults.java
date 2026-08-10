package ai.nomoclaw.bot.model;

import ai.nomoclaw.bot.modelconfig.ModelTypes;
import ai.nomoclaw.bot.modelconfig.model.ModelConfigDto;

import java.util.List;

public final class ModelProviderDefaults {

    private ModelProviderDefaults() {
    }

    public static List<ModelConfigDto.Provider> providers() {
        ModelConfigDto.ModelCapabilities text = capabilities(false, false, false, false, true);
        ModelConfigDto.ModelCapabilities vision = capabilities(false, true, false, false, true);
        ModelConfigDto.ModelCapabilities audioVision = capabilities(false, true, true, false, true);
        ModelConfigDto.ModelCapabilities videoVision = capabilities(false, true, true, true, true);
        return List.of(
                provider("codex", "Codex", "Codex Login", false, false, true,
                        "https://chatgpt.com/backend-api/codex", "", "gpt-5.4", List.of(
                                model("gpt-5.5", "5.5", vision, 0, 0, 0),
                                model("gpt-5.6-sol", "5.6 Sol", vision, 0, 0, 0),
                                model("gpt-5.6-terra", "5.6 Terra", vision, 0, 0, 0),
                                model("gpt-5.6-luna", "5.6 Luna", vision, 0, 0, 0),
                                model("gpt-5.4", "5.4", vision, 0, 0, 0),
                                model("gpt-5.4-mini", "5.4 Mini", vision, 0, 0, 0))),
                provider("deepseek", "DeepSeek", "OpenAI Compatible", false, true, true,
                        "https://api.deepseek.com", "", "deepseek-v4-flash", List.of(
                                model("deepseek-v4-pro", "DeepSeek V4 Pro", text, 1048576, 0, 393216),
                                model("deepseek-v4-flash", "DeepSeek V4 Flash", text, 1048576, 0, 393216))),
                provider("dashscope", "DashScope", "OpenAI Compatible", false, true, true,
                        "https://dashscope.aliyuncs.com/compatible-mode/v1", "", "qwen3.7-max", List.of(
                                embeddingModel("text-embedding-v4", "text-embedding-v4"),
                                model("qwen3.7-max", "Qwen3.7 Max", text, 1000000, 0, 65536),
                                model("qwen3.5-plus", "Qwen3.5 Plus", videoVision, 1000000, 0, 65536),
                                model("qwen3.6-plus", "Qwen3.6 Plus", videoVision, 1000000, 0, 65536),
                                model("qwen3.7-plus", "Qwen3.7 Plus", videoVision, 1000000, 0, 65536))),
                provider("aliyun-codingplan", "Aliyun Coding Plan", "OpenAI Compatible", false, true, true,
                        "https://coding.dashscope.aliyuncs.com/v1", "", "qwen3.5-plus", List.of(
                                model("qwen3.5-plus", "Qwen3.5 Plus", vision, 0, 0, 0),
                                model("qwen3.7-plus", "Qwen3.7 Plus", text, 0, 0, 0),
                                model("glm-5", "GLM-5", text, 0, 0, 0),
                                model("MiniMax-M2.5", "MiniMax M2.5", text, 0, 0, 0),
                                model("kimi-k2.5", "Kimi K2.5", text, 0, 0, 0))),
                provider("kimi-cn", "Kimi", "OpenAI Compatible", false, true, true,
                        "https://api.moonshot.cn/v1", "", "kimi-k2.5", List.of(
                                model("kimi-k2.5", "Kimi K2.5", text, 0, 0, 0),
                                model("kimi-k2-0905-preview", "Kimi K2 0905 Preview", text, 0, 0, 0),
                                model("kimi-k2-0711-preview", "Kimi K2 0711 Preview", text, 0, 0, 0),
                                model("kimi-k2-turbo-preview", "Kimi K2 Turbo Preview", text, 0, 0, 0),
                                model("kimi-k2-thinking-turbo", "Kimi K2 Thinking Turbo", text, 0, 0, 0))),
                provider("openai", "OpenAI", "OpenAI Compatible", false, true, true,
                        "https://api.openai.com/v1", "", "gpt-5.4", List.of(
                                model("gpt-5.3", "GPT-5.3", audioVision, 0, 0, 0),
                                model("gpt-5.4", "GPT-5.4", audioVision, 0, 0, 0),
                                model("gpt-4.1", "GPT-4.1", capabilities(false, true, false, false, false), 0, 0, 0),
                                model("gpt-4o", "GPT-4o", audioVision, 0, 0, 0))),
                provider("gemini", "Google Gemini", "Gemini Native", false, true, true,
                        "https://generativelanguage.googleapis.com", "", "gemini-2.5-pro", List.of(
                                model("gemini-2.5-pro", "Gemini 2.5 Pro", audioVision, 0, 0, 0),
                                model("gemini-2.5-flash", "Gemini 2.5 Flash", audioVision, 0, 0, 0),
                                model("gemini-2.0-flash", "Gemini 2.0 Flash", vision, 0, 0, 0))),
                provider("minimax-cn", "MiniMax", "Anthropic Compatible", false, true, true,
                        "https://api.minimaxi.com/anthropic", "", "MiniMax-M2.5", List.of(
                                model("MiniMax-M2.5", "MiniMax M2.5", text, 0, 0, 0),
                                model("MiniMax-M2.5-highspeed", "MiniMax M2.5 Highspeed", capabilities(false, false, false, false, false), 0, 0, 0),
                                model("MiniMax-M2.7", "MiniMax M2.7", text, 0, 0, 0),
                                model("MiniMax-M2.7-highspeed", "MiniMax M2.7 Highspeed", capabilities(false, false, false, false, false), 0, 0, 0))),
                provider("ollama", "Ollama", "Local Service", true, false, false,
                        "http://127.0.0.1:11434", "", "", List.of())
        );
    }

    private static ModelConfigDto.Provider provider(String id, String name, String protocol, boolean local,
                                                     boolean requireApiKey, boolean freezeUrl, String baseUrl,
                                                     String apiKey, String defaultModel, List<ModelConfigDto.Model> models) {
        return new ModelConfigDto.Provider(id, name, protocol, local, requireApiKey, freezeUrl, baseUrl, apiKey,
                false, "missing", "", defaultModel, models);
    }

    private static ModelConfigDto.Model model(String id, String name, ModelConfigDto.ModelCapabilities capabilities,
                                              int contextWindow, int maxInputTokens, int maxOutputTokens) {
        return new ModelConfigDto.Model(id, name, ModelTypes.TEXT_GENERATION, capabilities, contextWindow,
                maxInputTokens, maxOutputTokens, false, "builtin");
    }

    private static ModelConfigDto.Model embeddingModel(String id, String name) {
        return new ModelConfigDto.Model(id, name, ModelTypes.EMBEDDING, ModelConfigDto.ModelCapabilities.none(),
                0, 0, 0, false, "builtin");
    }

    private static ModelConfigDto.ModelCapabilities capabilities(boolean toolCalling, boolean imageRecognition,
                                                                   boolean audioRecognition, boolean videoRecognition,
                                                                   boolean reasoning) {
        return new ModelConfigDto.ModelCapabilities(toolCalling, imageRecognition, audioRecognition,
                videoRecognition, reasoning);
    }
}
