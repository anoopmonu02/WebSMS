package com.smsweb.sms.services.security;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientIpResolverTest {

    private ClientIpResolver resolver(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return new ClientIpResolver(new ThrottleSettings(m::get));
    }

    @Test
    void remoteAddrModeIgnoresHeader() {
        ClientIpResolver r = resolver();
        assertEquals("203.0.113.5", r.choose("remote-addr", "203.0.113.5", "1.2.3.4"));
    }

    @Test
    void cloudflareHeaderUsedOnlyFromTrustedPeer() {
        ClientIpResolver r = resolver("app.security.client-ip-mode", "cf-connecting-ip");
        assertEquals("198.51.100.7", r.choose("cf-connecting-ip", "172.18.0.2", "198.51.100.7"));
        assertEquals("203.0.113.5", r.choose("cf-connecting-ip", "203.0.113.5", "198.51.100.7"));
    }

    @Test
    void invalidOrMissingValuesFallBack() {
        ClientIpResolver r = resolver();
        assertEquals("172.18.0.2", r.choose("cf-connecting-ip", "172.18.0.2", "not-an-ip"));
        assertEquals("172.18.0.2", r.choose("cf-connecting-ip", "172.18.0.2", null));
        assertEquals("unknown", r.choose("remote-addr", null, null));
        assertEquals("unknown", r.choose("remote-addr", "evil.example.com", null));
        assertEquals("172.18.0.2", r.choose("cf-connecting-ip", "172.18.0.2", "999.1.1.1"));
    }

    @Test
    void ipv6AndCidr() {
        ClientIpResolver r = resolver();
        assertEquals("2001:db8:0:0:0:0:0:1".replace(":0:0:0:0:0:", ":0:0:0:0:0:"),
                ClientIpResolver.normalize("2001:db8:0:0:0:0:0:1"));
        assertEquals("0:0:0:0:0:0:0:1", r.choose("remote-addr", "::1", null));
        ClientIpResolver.Cidr c = ClientIpResolver.Cidr.parse("10.0.0.0/8");
        assertTrue(c.contains(ClientIpResolver.literal("10.200.1.1")));
        assertFalse(c.contains(ClientIpResolver.literal("11.0.0.1")));
        assertNull(ClientIpResolver.Cidr.parse("10.0.0.0/99"));
        ClientIpResolver.Cidr odd = ClientIpResolver.Cidr.parse("172.16.0.0/12");
        assertTrue(odd.contains(ClientIpResolver.literal("172.31.255.255")));
        assertFalse(odd.contains(ClientIpResolver.literal("172.32.0.1")));
    }
}
