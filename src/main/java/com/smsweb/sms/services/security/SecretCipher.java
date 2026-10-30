package com.smsweb.sms.services.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * NEW component (feature: encrypted secrets in system_config). Reversible AES-256-GCM
 * encryption for secrets the app must be able to read back later (SMTP app passwords,
 * and later Telegram/WhatsApp tokens). It cannot be a hash: SMTP needs the real value.
 *
 * The master key is NEVER stored in the database. It comes from the environment variable
 * SMS_CONFIG_ENCRYPTION_KEY (Base64 of exactly 32 random bytes, e.g. `openssl rand -base64 32`).
 *
 * Stored format: "ENC:v1:" + Base64( 12-byte random IV || ciphertext+tag ). A value without
 * the prefix is treated as a legacy plain-text value, so existing rows keep working until
 * they are upgraded (see ConfigSecretMigrator).
 *
 * Behaviour when the key is missing or invalid (the app still starts, nothing breaks):
 *  - decrypt() of a legacy plain value still works;
 *  - decrypt() of an encrypted value throws IllegalStateException (key not available);
 *  - encrypt() throws IllegalStateException, so callers must refuse to save a new secret.
 *
 * Neither the key nor any secret is ever written to a log.
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    public static final String PREFIX = "ENC:v1:";

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key; // null when the master key is not configured / invalid

    public SecretCipher(@Value("${SMS_CONFIG_ENCRYPTION_KEY:}") String base64Key) {
        this.key = parseKey(base64Key);
    }

    /** True when a valid master key is available, i.e. secrets can be encrypted and decrypted. */
    public boolean isConfigured() {
        return key != null;
    }

    /** True when the value carries the encrypted-value prefix. */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Encrypts a plain secret. Every call produces a different result (random IV).
     * @throws IllegalStateException if the master key is not configured
     */
    public String encrypt(String plain) {
        if (plain == null) {
            throw new IllegalArgumentException("Nothing to encrypt");
        }
        requireKey("encrypt");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt the value.", e);
        }
    }

    /**
     * Returns the plain secret. A null value returns null; a value without the prefix is a
     * legacy plain-text value and is returned unchanged.
     * @throws IllegalStateException if the value is encrypted but the key is missing/wrong,
     *                               or the stored value is corrupted
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return stored;
        }
        requireKey("decrypt");
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (in.length <= IV_BYTES) {
                throw new IllegalStateException("The stored encrypted value is corrupted.");
            }
            byte[] iv = Arrays.copyOfRange(in, 0, IV_BYTES);
            byte[] cipherText = Arrays.copyOfRange(in, IV_BYTES, in.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Wrong key, tampered or truncated value, or invalid Base64 — never include the value.
            throw new IllegalStateException(
                    "Could not decrypt the stored value (wrong SMS_CONFIG_ENCRYPTION_KEY or corrupted data).", e);
        }
    }

    private void requireKey(String action) {
        if (key == null) {
            throw new IllegalStateException("Cannot " + action
                    + ": SMS_CONFIG_ENCRYPTION_KEY is not set (or is invalid) on the server.");
        }
    }

    private static SecretKeySpec parseKey(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("SMS_CONFIG_ENCRYPTION_KEY is not set — new secrets cannot be saved and encrypted ones cannot be read. "
                    + "Existing plain-text values keep working until the key is configured.");
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != KEY_BYTES) {
                log.error("SMS_CONFIG_ENCRYPTION_KEY must be Base64 of exactly {} bytes (got {}). Encryption is disabled.",
                        KEY_BYTES, raw.length);
                return null;
            }
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException e) {
            log.error("SMS_CONFIG_ENCRYPTION_KEY is not valid Base64. Encryption is disabled.");
            return null;
        }
    }
}
