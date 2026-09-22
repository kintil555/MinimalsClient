package com.minimals.client.dressing;

import com.minimals.client.MinimalClientMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Talks directly to Mojang's account services (api.minecraftservices.com) using the same
 * access token the game itself is logged in with ({@link Minecraft#getUser()}). This is the
 * same HTTP API the official Minecraft launcher / minecraft.net skin page use - there is no
 * separate "in-game" skin API. A changed skin/cape is only reflected in the signed
 * {@code GameProfile} textures property that a server hands out when the client joins, so a
 * change made here will not retroactively repaint players already in a world; see
 * {@link DressingRoomScreen} for the local "best effort" refresh this mod does instead of
 * forcing a reconnect.
 */
public final class MojangSkinService {

    private static final String SKINS_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile/skins";
    private static final String ACTIVE_CAPE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile/capes/active";
    private static final Executor IO = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "minimals-skin-service");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private MojangSkinService() {
    }

    public enum Model {
        CLASSIC("classic"), SLIM("slim");

        /** The public minecraftservices.com "variant" string - unrelated to the game's internal
         *  {@code PlayerModelType} enum (SLIM/WIDE with legacy id "slim"/"default"); the public
         *  upload API has always used "classic"/"slim" and is unaffected by internal renames. */
        public final String apiName;

        Model(String apiName) {
            this.apiName = apiName;
        }

        public static Model from(net.minecraft.world.entity.player.PlayerModelType type) {
            return type == net.minecraft.world.entity.player.PlayerModelType.SLIM ? SLIM : CLASSIC;
        }
    }

    public record Result(boolean success, String message) {
        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /** Access token of the currently logged-in Microsoft/Mojang account, or null offline. */
    private static String accessToken() {
        try {
            String token = Minecraft.getInstance().getUser().getAccessToken();
            return token == null || token.isBlank() ? null : token;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isLoggedIn() {
        return accessToken() != null;
    }

    /**
     * Uploads a local PNG (64x64 or 64x32) as the account's new skin. Mojang's endpoint accepts
     * a multipart/form-data body with a "variant" field (classic/slim) and a "file" field.
     */
    public static CompletableFuture<Result> uploadSkin(Path pngFile, Model model) {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return Result.fail("Not logged in to a Microsoft/Mojang account.");
            }
            try {
                byte[] bytes = Files.readAllBytes(pngFile);
                if (bytes.length > 24 * 1024) {
                    return Result.fail("File is too large (max 24 KB for a skin PNG).");
                }
                String boundary = "MinimalsDressing" + UUID.randomUUID();
                byte[] body = buildMultipart(boundary, model.apiName, pngFile.getFileName().toString(), bytes);

                HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS_ENDPOINT))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    return Result.ok("Skin uploaded.");
                }
                return Result.fail(describeError(response.statusCode(), response.body()));
            } catch (IOException | InterruptedException e) {
                MinimalClientMod.LOGGER.warn("Dressing room: skin upload failed", e);
                return Result.fail("Network error: " + e.getMessage());
            }
        }, IO);
    }

    /**
     * Activates one of the capes already unlocked on the account (Mojang does not let arbitrary
     * cape images be uploaded - capes are a fixed set granted to the account). {@code capeId} is
     * the id from the profile's own cape list.
     */
    public static CompletableFuture<Result> activateCape(String capeId) {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return Result.fail("Not logged in to a Microsoft/Mojang account.");
            }
            try {
                String json = "{\"capeId\":\"" + capeId + "\"}";
                HttpRequest request = HttpRequest.newBuilder(URI.create(ACTIVE_CAPE_ENDPOINT))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    return Result.ok("Cape equipped.");
                }
                return Result.fail(describeError(response.statusCode(), response.body()));
            } catch (IOException | InterruptedException e) {
                MinimalClientMod.LOGGER.warn("Dressing room: cape update failed", e);
                return Result.fail("Network error: " + e.getMessage());
            }
        }, IO);
    }

    /** Removes the currently active cape (bare back). */
    public static CompletableFuture<Result> clearCape() {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return Result.fail("Not logged in to a Microsoft/Mojang account.");
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(ACTIVE_CAPE_ENDPOINT))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .DELETE()
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    return Result.ok("Cape removed.");
                }
                return Result.fail(describeError(response.statusCode(), response.body()));
            } catch (IOException | InterruptedException e) {
                MinimalClientMod.LOGGER.warn("Dressing room: cape clear failed", e);
                return Result.fail("Network error: " + e.getMessage());
            }
        }, IO);
    }

    private static String describeError(int status, String body) {
        return switch (status) {
            case 401, 403 -> "Session expired - restart the game and log in again.";
            case 429 -> "Rate limited by Mojang - try again in a bit.";
            default -> "Mojang API error (" + status + ").";
        };
    }

    private static byte[] buildMultipart(String boundary, String variant, String fileName, byte[] pngBytes) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        String nl = "\r\n";
        out.write(("--" + boundary + nl).getBytes());
        out.write(("Content-Disposition: form-data; name=\"variant\"" + nl + nl).getBytes());
        out.write((variant + nl).getBytes());

        out.write(("--" + boundary + nl).getBytes());
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"" + nl).getBytes());
        out.write(("Content-Type: image/png" + nl + nl).getBytes());
        out.write(pngBytes);
        out.write(nl.getBytes());

        out.write(("--" + boundary + "--" + nl).getBytes());
        return out.toByteArray();
    }
}
