package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {

    private static String newKey(int bytes, byte fill) {
        byte[] raw = new byte[bytes];
        java.util.Arrays.fill(raw, fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private static final String KEY_A = newKey(32, (byte) 7);
    private static final String KEY_B = newKey(32, (byte) 9);

    @Test
    void roundTripReturnsOriginal() {
        SecretCipher c = new SecretCipher(KEY_A);
        String enc = c.encrypt("abcd efgh ijkl mnop");
        assertTrue(enc.startsWith(SecretCipher.PREFIX));
        assertNotEquals("abcd efgh ijkl mnop", enc);
        assertEquals("abcd efgh ijkl mnop", c.decrypt(enc));
    }

    @Test
    void sameSecretEncryptsDifferentlyEachTime() {
        SecretCipher c = new SecretCipher(KEY_A);
        assertNotEquals(c.encrypt("secret"), c.encrypt("secret"));
    }

    @Test
    void unicodeAndLongValuesRoundTrip() {
        SecretCipher c = new SecretCipher(KEY_A);
        String v = "पासवर्ड-" + "x".repeat(100);
        assertEquals(v, c.decrypt(c.encrypt(v)));
    }

    @Test
    void legacyPlainValueIsReturnedUnchanged() {
        assertEquals("plainpass", new SecretCipher(KEY_A).decrypt("plainpass"));
        assertEquals("plainpass", new SecretCipher("").decrypt("plainpass"));
        assertNull(new SecretCipher(KEY_A).decrypt(null));
    }

    @Test
    void wrongKeyFailsClearly() {
        String enc = new SecretCipher(KEY_A).encrypt("secret");
        assertThrows(IllegalStateException.class, () -> new SecretCipher(KEY_B).decrypt(enc));
    }

    @Test
    void tamperedOrTruncatedValueFails() {
        SecretCipher c = new SecretCipher(KEY_A);
        String enc = c.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(enc.substring(SecretCipher.PREFIX.length()));
        raw[raw.length - 1] ^= 1;
        String tampered = SecretCipher.PREFIX + Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> c.decrypt(tampered));
        assertThrows(IllegalStateException.class, () -> c.decrypt(SecretCipher.PREFIX + "AAAA"));
        assertThrows(IllegalStateException.class, () -> c.decrypt(SecretCipher.PREFIX + "%%%not-base64"));
    }

    @Test
    void missingOrInvalidKeyDisablesEncryptionButDoesNotCrash() {
        for (String bad : new String[]{"", "   ", "not base64 !!", newKey(16, (byte) 1)}) {
            SecretCipher c = new SecretCipher(bad);
            assertFalse(c.isConfigured());
            assertThrows(IllegalStateException.class, () -> c.encrypt("x"));
            String enc = new SecretCipher(KEY_A).encrypt("x");
            assertThrows(IllegalStateException.class, () -> c.decrypt(enc));
        }
        assertTrue(new SecretCipher(KEY_A).isConfigured());
    }
}
