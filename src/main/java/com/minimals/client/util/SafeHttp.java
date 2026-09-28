package com.minimals.client.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Shared, defensive HTTP helpers. Two guarantees the plain {@code BodyHandlers.ofByteArray()}
 * does not give:
 * <ul>
 *   <li>the body is read through a hard byte cap while streaming, so a hostile or broken server
 *       (chunked response with no / a lying Content-Length) can never fill the heap;</li>
 *   <li>a texture URL is only fetched when it is https on Mojang's own CDN, so a crafted profile
 *       can never make the client contact an arbitrary host.</li>
 * </ul>
 */
public final class SafeHttp {

    /** One client for the whole mod; redirects stay NEVER so a token can not follow a redirect. */
    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /** Minecraft names are 3-16 chars of [A-Za-z0-9_] (1 accepted here for lookups). */
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** A real skin/cape PNG is a few KB; this is generous while still tiny. */
    public static final int MAX_TEXTURE_BYTES = 512 * 1024;
    /** Profile / lookup JSON is small; cap so a bad endpoint can not stream forever. */
    public static final int MAX_JSON_BYTES = 256 * 1024;

    private SafeHttp() {
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /** True only for {@code https://textures.minecraft.net/...} (or another *.minecraft.net host). */
    public static boolean isMojangTextureUrl(String url) {
        if (url == null || url.length() > 2048) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null
                    && (host.equals("textures.minecraft.net") || host.endsWith(".minecraft.net"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * GETs {@code uri} and returns the body only when the status is 2xx and the body fits in
     * {@code maxBytes}; otherwise returns null. Never buffers more than {@code maxBytes + 1}.
     */
    public static byte[] getCapped(URI uri, Duration timeout, int maxBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).GET().build();
        HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > maxBytes) {
                return null;
            }
            return readCapped(in, maxBytes);
        }
    }

    /** Reads at most {@code maxBytes}; returns null if the stream holds more than that. */
    static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 16 * 1024));
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
