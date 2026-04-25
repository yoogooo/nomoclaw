package ai.nomoclaw.bot.agent;

import ai.nomoclaw.bot.orchestrator.ModelCatalogService;
import ai.nomoclaw.bot.workspace.NomoClawPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCatalogServiceTests {

    @TempDir
    Path tempDir;
    Path originalRoot;

    @BeforeEach
    void rememberRoot() {
        originalRoot = NomoClawPaths.root();
    }

    @AfterEach
    void restoreRoot() {
        NomoClawPaths.configureRoot(originalRoot);
    }

    @Test
    void resolvesExactProviderModelMatch() {
        NomoClawPaths.configureRoot(tempDir);
        ModelCatalogService service = new ModelCatalogService(HttpClient.newHttpClient(), "");

        var metadata = service.resolve("openai", "gpt-4.1");

        assertThat(metadata.matched()).isTrue();
        assertThat(metadata.inputModalities()).contains("text", "image");
        assertThat(metadata.uploadPolicy().enabled()).isTrue();
        assertThat(metadata.uploadPolicy().maxImagesPerMessage()).isEqualTo(1500);
    }

    @Test
    void resolvesAliasAndGlobalModelMatch() {
        NomoClawPaths.configureRoot(tempDir);
        ModelCatalogService service = new ModelCatalogService(HttpClient.newHttpClient(), "");

        var alias = service.resolve("dashscope", "qwen3-max-2026-01-23");
        var global = service.resolve("aliyun-codingplan", "gpt-4o");

        assertThat(alias.matched()).isTrue();
        assertThat(global.matched()).isTrue();
        assertThat(global.source()).isEqualTo("bundled");
    }

    @Test
    void fallsBackToTextOnlyForUnknownModel() {
        NomoClawPaths.configureRoot(tempDir);
        ModelCatalogService service = new ModelCatalogService(HttpClient.newHttpClient(), "");

        var metadata = service.resolve("openai", "unknown-model");

        assertThat(metadata.matched()).isFalse();
        assertThat(metadata.inputModalities()).containsExactly("text");
        assertThat(metadata.uploadPolicy().enabled()).isFalse();
        assertThat(metadata.source()).isEqualTo("fallback");
    }

    @Test
    void localCustomCatalogOverridesBundledCatalog() throws Exception {
        NomoClawPaths.configureRoot(tempDir);
        Path catalogDir = tempDir.resolve("model-catalog");
        Files.createDirectories(catalogDir);
        Files.writeString(catalogDir.resolve("custom-models.json"), """
                {
                  "schemaVersion": 1,
                  "catalogVersion": "custom",
                  "generatedAt": "2026-04-15T00:00:00Z",
                  "models": [
                    {
                      "providerIds": ["openai"],
                      "modelId": "gpt-4.1",
                      "displayName": "Custom GPT-4.1",
                      "aliases": [],
                      "inputModalities": ["text"],
                      "outputModalities": ["text"],
                      "reasoning": true,
                      "contextWindowTokens": 123,
                      "maxInputTokens": 0,
                      "maxOutputTokens": 0,
                      "uploadPolicy": {
                        "enabled": false,
                        "allowedMimeGroups": [],
                        "maxFilesPerMessage": 0,
                        "maxImagesPerMessage": 0,
                        "maxFileBytes": 0,
                        "maxTotalBytes": 0,
                        "singleMimeGroupOnly": false,
                        "allowMixedImageAndFile": false
                      },
                      "sourceRefs": [],
                      "lastVerifiedAt": "2026-04-15",
                      "confidence": "high"
                    }
                  ]
                }
                """);
        ModelCatalogService service = new ModelCatalogService(HttpClient.newHttpClient(), "");

        var metadata = service.resolve("openai", "gpt-4.1");

        assertThat(metadata.matched()).isTrue();
        assertThat(metadata.displayName()).isEqualTo("Custom GPT-4.1");
        assertThat(metadata.contextWindowTokens()).isEqualTo(123);
        assertThat(metadata.uploadPolicy().enabled()).isFalse();
        assertThat(metadata.source()).contains("custom");
    }
}
