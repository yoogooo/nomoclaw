package ai.nomoclaw.bot.orchestrator;

import ai.nomoclaw.bot.llm.config.ModelConfigCryptoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class ModelConfigCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String ENC_PREFIX = "enc:";
    private static final String PLAIN_PREFIX = "plain:";

    private final ModelConfigCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ModelConfigCryptoService(ModelConfigCryptoProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        if (!hasEncryptionKey()) {
            log.warn("llm.model-config.encryption-key is not configured, api key will be stored in plaintext wrapper");
            return PLAIN_PREFIX + plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return ENC_PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new IllegalStateException("failed to encrypt model config api key", ex);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        if (ciphertext.startsWith(PLAIN_PREFIX)) {
            return ciphertext.substring(PLAIN_PREFIX.length());
        }
        try {
            String normalized = ciphertext.startsWith(ENC_PREFIX)
                    ? ciphertext.substring(ENC_PREFIX.length())
                    : ciphertext;
            byte[] raw = Base64.getDecoder().decode(normalized);
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to decrypt model config api key", ex);
        }
    }

    private boolean hasEncryptionKey() {
        String rawKey = properties.getEncryptionKey();
        return rawKey != null && !rawKey.isBlank();
    }

    private SecretKeySpec secretKey() {
        String rawKey = properties.getEncryptionKey();
        if (rawKey == null || rawKey.isBlank()) {
            throw new IllegalStateException("llm.model-config.encryption-key is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("failed to initialize model config encryption key", ex);
        }
    }
}
