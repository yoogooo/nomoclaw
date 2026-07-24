package ai.nomoclaw.bot.knowledge.ingestion;

import org.springframework.stereotype.Component;

/**
 * Estimates CJK characters individually and Latin runs at roughly four characters per token.
 */
@Component
public class UnicodeTokenEstimator implements TokenEstimator {
    @Override
    public int estimate(String text) {
        if (text == null || text.isBlank()) return 0;
        int cjk = 0;
        int latinCharacters = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (isCjk(codePoint)) cjk++;
            else if (!Character.isWhitespace(codePoint)) latinCharacters++;
            offset += Character.charCount(codePoint);
        }
        return Math.max(1, cjk + (latinCharacters + 3) / 4);
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL;
    }
}
