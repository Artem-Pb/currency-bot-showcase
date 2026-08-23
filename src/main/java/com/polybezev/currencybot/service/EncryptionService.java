package com.polybezev.currencybot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM symmetric encryption service for protecting exchange API credentials at rest.
 * <p>
 * <b>Algorithm:</b> AES/GCM/NoPadding with a 256-bit key and a 96-bit (12-byte) random IV.
 * GCM mode provides both confidentiality and integrity — tampered ciphertext will fail decryption.
 * <p>
 * <b>Storage format:</b> {@code Base64(IV ‖ ciphertext)} — IV and ciphertext are concatenated
 * before Base64-encoding so that a single database column holds all decryption material.
 * The IV is not secret but must be unique per encryption; using {@link SecureRandom} guarantees
 * statistical uniqueness without coordination.
 * <p>
 * <b>Key management:</b> the master key is read once from the {@code encryption.key} property
 * (backed by the {@code ENCRYPTION_KEY} environment variable) and held as a {@link SecretKey}.
 * It is never logged and never written to persistent storage.
 * <p>
 * If {@code ENCRYPTION_KEY} is absent or empty the service logs a warning and uses a fixed
 * fallback key — acceptable for local development, but credentials encrypted this way cannot
 * be decrypted in a different environment.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH     = 12;   // 96 bits — recommended for GCM
    private static final int    TAG_LENGTH    = 128;  // authentication tag bits

    private final SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Initialises the service with the master encryption key from configuration.
     * The key must be a Base64-encoded 32-byte (256-bit) random value.
     *
     * @param base64Key Base64-encoded AES-256 key from {@code encryption.key} property;
     *                  an empty string triggers a fallback key with a warning
     */
    public EncryptionService(@Value("${encryption.key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("ENCRYPTION_KEY not set — using insecure fallback key. Set the env var for production.");
            byte[] fallback = "fallback-dev-key-32bytes-padding!".getBytes();
            this.masterKey = new SecretKeySpec(fallback, "AES");
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
        }
    }

    /**
     * Encrypts {@code plaintext} with AES-256-GCM and returns the result as
     * {@code Base64(IV ‖ ciphertext)}.
     * <p>
     * A fresh random IV is generated for every call — calling this method twice with
     * the same plaintext produces different outputs, which is the expected behaviour.
     *
     * @param plaintext the value to encrypt (e.g. an exchange API key or secret)
     * @return Base64-encoded {@code IV ‖ ciphertext} string safe to store in the database
     * @throws RuntimeException if the JVM does not support AES/GCM (should never happen on Java 17)
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     * <p>
     * Extracts the IV from the first {@value #IV_LENGTH} bytes of the decoded input,
     * then decrypts the remaining bytes. GCM authentication tag validation is implicit —
     * any data corruption or wrong key will cause an {@link javax.crypto.AEADBadTagException}.
     *
     * @param encrypted Base64-encoded {@code IV ‖ ciphertext} as returned by {@link #encrypt}
     * @return original plaintext
     * @throws RuntimeException if decryption fails (wrong key, corrupted data, or truncated input)
     */
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);

            byte[] iv         = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
