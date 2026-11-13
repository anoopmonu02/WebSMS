package com.smsweb.sms.services.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiFunction;

/**
 * Checks a Cloudflare Turnstile answer on the server. Used only on the web sign-in, only after a few wrong
 * attempts, and only when switched on with both keys present (see {@link ThrottleSettings#turnstileActive()}).
 *
 * <p>Works on any site, whether or not it is behind Cloudflare; the server only needs outbound HTTPS to Cloudflare.
 * The secret key never leaves the server. If Cloudflare cannot be reached in 3 seconds the result is
 * {@link Result#UNAVAILABLE} and the caller lets the sign-in continue (fail-open), so a Cloudflare outage cannot lock
 * everyone out. Never throws.</p>
 */
@Service
public class TurnstileService {

    public enum Result { PASSED, FAILED, UNAVAILABLE }

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_TOKEN_LENGTH = 2048; // Cloudflare tokens are well under this

    private final ThrottleSettings settings;
    private final BiFunction<String, String, String> post; // (url, form body) -> response body
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public TurnstileService(ThrottleSettings settings) {
        this(settings, defaultPost());
    }

    /** For tests: supply the HTTP call. */
    TurnstileService(ThrottleSettings settings, BiFunction<String, String, String> post) {
        this.settings = settings;
        this.post = post;
    }

    public boolean isActive() {
        return settings.turnstileActive();
    }

    /**
     * @param token the {@code cf-turnstile-response} value the browser sent (may be null or blank)
     * @param ip    the visitor's address, passed to Cloudflare as an extra check (may be null)
     */
    public Result verify(String token, String ip) {
        try {
            if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
                return Result.FAILED;
            }
            StringBuilder form = new StringBuilder();
            form.append("secret=").append(enc(settings.turnstileSecretKey()));
            form.append("&response=").append(enc(token));
            if (ip != null && !ip.isBlank() && !"unknown".equals(ip)) {
                form.append("&remoteip=").append(enc(ip));
            }
            String body = post.apply(settings.turnstileVerifyUrl(), form.toString());
            JsonNode json = mapper.readTree(body);
            if (json == null || !json.has("success")) {
                log.warn("SECURITY turnstile: unexpected answer from Cloudflare; allowing the sign-in");
                return Result.UNAVAILABLE;
            }
            return json.get("success").asBoolean(false) ? Result.PASSED : Result.FAILED;
        } catch (Exception e) {
            log.warn("SECURITY turnstile: Cloudflare could not be reached ({}); allowing the sign-in", e.getClass().getSimpleName());
            return Result.UNAVAILABLE;
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static BiFunction<String, String, String> defaultPost() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return (url, form) -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() / 100 != 2) {
                    throw new IllegalStateException("HTTP " + res.statusCode());
                }
                return res.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
