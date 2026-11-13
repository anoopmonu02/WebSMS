package com.smsweb.sms.services.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the visitor's IP address for throttling.
 *
 * <p>Default ({@code app.security.client-ip-mode=remote-addr}): the address the servlet container reports. With the
 * existing {@code server.forward-headers-strategy=native}, Tomcat already replaces it with the visitor's address
 * when the connection comes from an internal proxy (a tunnel or nginx on the same machine or Docker network).</p>
 *
 * <p>Optional ({@code cf-connecting-ip}): read Cloudflare's {@code CF-Connecting-IP} header, but only when the
 * connection itself comes from one of {@code app.security.trusted-proxies}. A header sent by anyone else is ignored,
 * so a visitor cannot invent an address to dodge the limits. Switch it on only after confirming that the server is
 * reachable only through Cloudflare or the tunnel.</p>
 *
 * <p>Never throws; when nothing usable is found it returns {@code "unknown"}.</p>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);
    private static final String UNKNOWN = "unknown";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final String mode;
    private final List<Cidr> trusted = new ArrayList<>();
    private volatile boolean warnedUntrusted;

    @Autowired
    public ClientIpResolver(ThrottleSettings settings) {
        this.mode = settings.clientIpMode();
        for (String c : settings.trustedProxies()) {
            Cidr parsed = Cidr.parse(c);
            if (parsed == null) {
                log.warn("Ignoring invalid entry in app.security.trusted-proxies: '{}'", Masking.plain(c));
            } else {
                trusted.add(parsed);
            }
        }
    }

    public String resolve(HttpServletRequest request) {
        try {
            String remote = request.getRemoteAddr();
            String header = ThrottleSettings.IP_MODE_CF.equals(mode) ? request.getHeader("CF-Connecting-IP") : null;
            if (header != null && !warnedUntrusted && !isTrusted(remote)) {
                warnedUntrusted = true;
                log.warn("SECURITY client-ip-mode is cf-connecting-ip but a request with that header came from an address "
                        + "that is not in app.security.trusted-proxies ({}); the header was ignored. If your proxy is "
                        + "legitimate, add its address to app.security.trusted-proxies.", Masking.plain(remote));
            }
            return choose(mode, remote, header);
        } catch (RuntimeException e) {
            return UNKNOWN;
        }
    }

    /** The decision itself, free of servlet types so tests can run it directly. */
    String choose(String mode, String remoteAddr, String cfHeader) {
        String remote = normalize(remoteAddr);
        if (ThrottleSettings.IP_MODE_CF.equals(mode) && cfHeader != null && isTrusted(remoteAddr)) {
            String fromHeader = normalize(cfHeader);
            if (fromHeader != null) {
                return fromHeader;
            }
        }
        return remote == null ? UNKNOWN : remote;
    }

    private boolean isTrusted(String address) {
        InetAddress a = literal(address);
        if (a == null) {
            return false;
        }
        for (Cidr c : trusted) {
            if (c.contains(a)) {
                return true;
            }
        }
        return false;
    }

    /** Canonical text of an IP literal, or null when the text is not one (never does a name lookup). */
    static String normalize(String text) {
        InetAddress a = literal(text);
        return a == null ? null : a.getHostAddress();
    }

    static InetAddress literal(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        try {
            if (IPV4.matcher(s).matches()) {
                for (String part : s.split("\\.")) {
                    if (Integer.parseInt(part) > 255) {
                        return null;
                    }
                }
                return InetAddress.getByName(s);
            }
            if (s.indexOf(':') >= 0 && IPV6.matcher(s).matches()) {
                return InetAddress.getByName(s);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** One trusted address range, for example 10.0.0.0/8. */
    static final class Cidr {
        private final byte[] network;
        private final int prefix;

        private Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        static Cidr parse(String text) {
            if (text == null) {
                return null;
            }
            String s = text.trim();
            int slash = s.indexOf('/');
            String addr = slash < 0 ? s : s.substring(0, slash);
            InetAddress a = literal(addr);
            if (a == null) {
                return null;
            }
            byte[] bytes = a.getAddress();
            int max = bytes.length * 8;
            int prefix = max;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(s.substring(slash + 1).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                if (prefix < 0 || prefix > max) {
                    return null;
                }
            }
            return new Cidr(bytes, prefix);
        }

        boolean contains(InetAddress other) {
            byte[] o = other.getAddress();
            if (o.length != network.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (o[i] != network[i]) {
                    return false;
                }
            }
            int rest = prefix % 8;
            if (rest == 0) {
                return true;
            }
            int mask = 0xFF << (8 - rest) & 0xFF;
            return (o[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
