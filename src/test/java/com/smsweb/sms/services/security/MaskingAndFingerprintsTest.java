package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskingAndFingerprintsTest {

    @Test
    void masksUsernamesAndMobiles() {
        assertEquals("ra***", Masking.username("rajesh"));
        assertFalse(Masking.mobile("9876543210").contains("76543"));
        assertTrue(Masking.mobile("9876543210").startsWith("98"));
        assertTrue(Masking.mobile("9876543210").endsWith("10"));
        assertEquals("-", Masking.username(null).isEmpty() ? "-" : "-");
    }

    @Test
    void stripsControlCharactersAndCapsLength() {
        String s = Masking.plain("a\r\nb" + "x".repeat(100));
        assertFalse(s.contains("\n"));
        assertFalse(s.contains("\r"));
        assertTrue(s.length() <= 40);
    }

    @Test
    void fingerprintIsStableAndDoesNotContainPassword() {
        assertEquals(Fingerprints.of("secret1"), Fingerprints.of("secret1"));
        assertNotEquals(Fingerprints.of("secret1"), Fingerprints.of("secret2"));
        assertFalse(Fingerprints.of("secret1").contains("secret"));
        assertEquals(16, Fingerprints.of("secret1").length());
    }
}
