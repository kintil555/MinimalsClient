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
    private static final String OWN_PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile";
    private static final String UUID_LOOKUP_ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_ENDPOINT = "https://sessionserver.mojang.com/session/minecraft/profile/";
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

    /** One cape already unlocked on the account, as returned by the profile endpoint. */
    public record Cape(String id, String name, boolean active, String url) {
    }

    /** Live skin/cape state of the logged-in account (URLs may be null). */
    public record Appearance(String skinUrl, Model model, String capeUrl) {
    }

    /** A skin texture URL fetched from another player's public profile, by username. */
    public record FetchedSkin(String username, String textureUrl) {
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

    /**
     * Lists the capes already unlocked on this account (own profile endpoint includes a "capes"
     * array; Mojang does not let arbitrary capes be uploaded, only one of these activated).
     */
    public static CompletableFuture<java.util.List<Cape>> fetchOwnCapes() {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return java.util.List.<Cape>of();
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(OWN_PROFILE_ENDPOINT))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    return java.util.List.<Cape>of();
                }
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(response.body())
                        .getAsJsonObject();
                com.google.gson.JsonArray capesArr = root.has("capes") ? root.getAsJsonArray("capes") : null;
                if (capesArr == null) {
                    return java.util.List.<Cape>of();
                }
                java.util.List<Cape> capes = new java.util.ArrayList<>();
                for (var el : capesArr) {
                    var o = el.getAsJsonObject();
                    String id = o.has("id") ? o.get("id").getAsString() : null;
                    String name = o.has("alias") ? o.get("alias").getAsString() : id;
                    boolean active = o.has("state") && "ACTIVE".equals(o.get("state").getAsString());
                    String url = o.has("url") ? o.get("url").getAsString() : null;
                    if (id != null) {
                        capes.add(new Cape(id, name, active, url));
                    }
                }
                return capes;
            } catch (Exception e) {
                MinimalClientMod.LOGGER.warn("Dressing room: fetching owned capes failed", e);
                return java.util.List.<Cape>of();
            }
        }, IO);
    }


    /**
     * Reads the account's CURRENT skin/cape straight from the authenticated profile endpoint
     * (real-time, unlike the session server / the signed GameProfile property the game holds).
     */
    public static CompletableFuture<Appearance> fetchOwnAppearance() {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return null;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(OWN_PROFILE_ENDPOINT))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    return null;
                }
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(response.body())
                        .getAsJsonObject();
                String skinUrl = null;
                Model model = Model.CLASSIC;
                if (root.has("skins")) {
                    for (var el : root.getAsJsonArray("skins")) {
                        var o = el.getAsJsonObject();
                        if (o.has("state") && "ACTIVE".equals(o.get("state").getAsString()) && o.has("url")) {
                            skinUrl = o.get("url").getAsString();
                            if (o.has("variant") && "SLIM".equalsIgnoreCase(o.get("variant").getAsString())) {
                                model = Model.SLIM;
                            }
                            break;
                        }
                    }
                }
                String capeUrl = null;
                if (root.has("capes")) {
                    for (var el : root.getAsJsonArray("capes")) {
                        var o = el.getAsJsonObject();
                        if (o.has("state") && "ACTIVE".equals(o.get("state").getAsString()) && o.has("url")) {
                            capeUrl = o.get("url").getAsString();
                            break;
                        }
                    }
                }
                return new Appearance(skinUrl, model, capeUrl);
            } catch (Exception e) {
                MinimalClientMod.LOGGER.warn("Dressing room: fetching own appearance failed", e);
                return null;
            }
        }, IO);
    }

    /**
     * Looks up a player's current skin by username via Mojang's public (unauthenticated)
     * endpoints: username -> uuid -> session profile -> base64 "textures" property -> skin URL.
     * Works for any player regardless of login state, since these endpoints are public.
     */
    public static CompletableFuture<FetchedSkin> fetchSkinByUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest lookup = HttpRequest.newBuilder(URI.create(UUID_LOOKUP_ENDPOINT + username))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> lookupResp = CLIENT.send(lookup, HttpResponse.BodyHandlers.ofString());
                if (lookupResp.statusCode() == 404 || lookupResp.statusCode() / 100 != 2) {
                    return null;
                }
                String uuid = com.google.gson.JsonParser.parseString(lookupResp.body())
                        .getAsJsonObject().get("id").getAsString();

                HttpRequest profile = HttpRequest.newBuilder(URI.create(SESSION_PROFILE_ENDPOINT + uuid))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> profileResp = CLIENT.send(profile, HttpResponse.BodyHandlers.ofString());
                if (profileResp.statusCode() / 100 != 2) {
                    return null;
                }
                var root = com.google.gson.JsonParser.parseString(profileResp.body()).getAsJsonObject();
                var properties = root.getAsJsonArray("properties");
                for (var el : properties) {
                    var o = el.getAsJsonObject();
                    if (!"textures".equals(o.get("name").getAsString())) {
                        continue;
                    }
                    String decoded = new String(java.util.Base64.getDecoder().decode(o.get("value").getAsString()));
                    var texRoot = com.google.gson.JsonParser.parseString(decoded).getAsJsonObject();
                    var textures = texRoot.getAsJsonObject("textures");
                    if (textures != null && textures.has("SKIN")) {
                        String url = textures.getAsJsonObject("SKIN").get("url").getAsString();
                        return new FetchedSkin(username, url);
                    }
                }
                return null;
            } catch (Exception e) {
                MinimalClientMod.LOGGER.warn("Dressing room: fetching skin by username failed", e);
                return null;
            }
        }, IO);
    }

    /**
     * Downloads a fetched skin's texture PNG and uploads it as this account's own skin. Mojang's
     * skin endpoint only accepts an uploaded file, not a URL, so applying someone else's skin
     * still goes through the same multipart upload as a local file.
     */
    public static CompletableFuture<Result> applyFetchedSkin(FetchedSkin skin, Model model) {
        return CompletableFuture.supplyAsync(() -> {
            String token = accessToken();
            if (token == null) {
                return Result.fail("Not logged in to a Microsoft/Mojang account.");
            }
            try {
                HttpRequest textureReq = HttpRequest.newBuilder(URI.create(skin.textureUrl()))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<byte[]> textureResp = CLIENT.send(textureReq, HttpResponse.BodyHandlers.ofByteArray());
                if (textureResp.statusCode() / 100 != 2) {
                    return Result.fail("Could not download that player's skin texture.");
                }
                byte[] bytes = textureResp.body();
                String boundary = "MinimalsDressing" + UUID.randomUUID();
                byte[] body = buildMultipart(boundary, model.apiName, skin.username() + ".png", bytes);

                HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS_ENDPOINT))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    return Result.ok("Skin applied from " + skin.username() + ".");
                }
                return Result.fail(describeError(response.statusCode(), response.body()));
            } catch (IOException | InterruptedException e) {
                MinimalClientMod.LOGGER.warn("Dressing room: applying fetched skin failed", e);
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
