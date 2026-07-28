package ai.nomoclaw.bot.modelconfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModelTypes {

    public static final String CHAT = "CHAT";
    public static final String EMBEDDING = "EMBEDDING";
    public static final String SPEECH = "SPEECH";
    public static final String IMAGE = "IMAGE";
    public static final String MODERATION = "MODERATION";
    public static final String REALTIME = "REALTIME";

    private static final Set<String> SUPPORTED = Set.of(CHAT, EMBEDDING, SPEECH, IMAGE, MODERATION, REALTIME);

    private ModelTypes() {
    }

    public static String sanitize(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED.contains(normalized) ? normalized : CHAT;
    }

    public static String inferFromModalities(List<String> inputModalities, List<String> outputModalities) {
        Set<String> modalities = new LinkedHashSet<>();
        addModalities(modalities, inputModalities);
        addModalities(modalities, outputModalities);
        if (modalities.contains("audio") && modalities.size() <= 2 && !modalities.contains("image") && !modalities.contains("video")) {
            return SPEECH;
        }
        return CHAT;
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
