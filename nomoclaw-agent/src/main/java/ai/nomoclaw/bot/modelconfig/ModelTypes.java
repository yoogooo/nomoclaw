package ai.nomoclaw.bot.modelconfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModelTypes {

    public static final String TEXT_GENERATION = "TEXT_GENERATION";
    public static final String IMAGE_GENERATION = "IMAGE_GENERATION";
    public static final String VIDEO_GENERATION = "VIDEO_GENERATION";
    public static final String AUDIO_GENERATION = "AUDIO_GENERATION";
    public static final String AUDIO_TRANSCRIPTION = "AUDIO_TRANSCRIPTION";
    public static final String EMBEDDING = "EMBEDDING";
    public static final String REALTIME = "REALTIME";

    private static final Set<String> SUPPORTED = Set.of(TEXT_GENERATION, IMAGE_GENERATION, VIDEO_GENERATION,
            AUDIO_GENERATION, AUDIO_TRANSCRIPTION, EMBEDDING, REALTIME);

    private ModelTypes() {
    }

    public static String sanitize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED.contains(normalized) ? normalized : TEXT_GENERATION;
    }

    public static String inferFromModalities(List<String> inputModalities, List<String> outputModalities) {
        Set<String> modalities = new LinkedHashSet<>();
        addModalities(modalities, inputModalities);
        addModalities(modalities, outputModalities);
        return TEXT_GENERATION;
    }

    private static void addModalities(Set<String> target, List<String> modalities) {
        if (modalities == null) {
            return;
        }
        for (String modality : modalities) {
            if (modality == null) {
                continue;
            }
            String normalized = modality.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                target.add(normalized);
            }
        }
    }
}
