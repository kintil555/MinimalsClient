package com.minimals.client.worldhost;

import com.minimals.client.worldhost.protocol.WHProtocolClient;
import com.minimals.client.worldhost.protocol.WHS2CMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the "friend join world" feature: connects to a World Host-compatible
 * relay server, tracks which friends are online in a joinable world, shows toast
 * notifications on invite/request, and bridges an incoming join through to the local
 * integrated server via {@link ProxyHostBridge}.
 *
 * This is a from-scratch client (not a fork) built against io.github.gaming32's
 * World Host protocol v7, talking to the public relay world-host.jemnetworks.com:9646 for
 * friend presence. Game traffic goes through the bundled e4mc relay (see {@link E4mcBridge}).
 */
public final class WorldHostManager {

    public static final Logger LOGGER = LoggerFactory.getLogger("MinimalsWorldHost");

    public static final String DEFAULT_HOST = "world-host.jemnetworks.com";

    private static WHProtocolClient client;
    private static ProxyHostBridge hostBridge;

    /** Friends currently known to be online in a joinable world: UUID -> their connection id. */
    private static final Map<UUID, Long> ONLINE_FRIENDS = new LinkedHashMap<>();

    /** How the world is being shared. LAN = vanilla only; MULTIPLAYER = also announced to friends. */
    public enum Mode { LAN, MULTIPLAYER }

    /** Set by OpenWorldScreen right before publishServer(); consumed by onWorldPublished(). */
    private static Mode pendingMode = Mode.LAN;
    private static boolean multiplayerActive;

    /** Marker put at the start of our world's MOTD when answering a friend's query; followed by the e4mc domain. */
    private static final String DOMAIN_MARKER = "minimals-e4mc:";
    /** Friend we sent a query to (waiting for their e4mc domain), or null. */
    private static UUID pendingQueryFriend;
    private static int pendingQueryTicks;
    private static final int QUERY_TIMEOUT_TICKS = 100; // ~5s, then fall back to the proxy join
    /** True once e4mc has a domain (or the wait timed out), i.e. friends may be told about the world. */
    private static boolean announceReady;
    private static int announceTimeoutTicks;
    private static final int ANNOUNCE_TIMEOUT_TICKS = 300; // ~15s, then fall back to the relay proxy
    private static boolean friendListenerRegistered;

    private static int reconnectDelayTicks;
    private static boolean initialized;
    /** Connection id of the world we're currently trying to join, or null if none. */
    private static Long attemptingToJoin;

    private WorldHostManager() {
    }

    // --- lifecycle -------------------------------------------------------

    public static void init() {
        if (initialized) return;
        initialized = true;
    }

    public static void setPendingMode(Mode mode) {
        pendingMode = mode;
        // e4mc's relay only runs for "Multiplayer"; plain LAN must stay private.
        E4mcBridge.setHostEnabled(mode == Mode.MULTIPLAYER);
    }

    /**
     * Called (on any thread) by {@link E4mcDomainHolder} when e4mc's relay assigned a domain.
     * Friends only get the Join button once the domain exists, so joining never lands on a
     * world that cannot be reached yet.
     */
    public static void onDomainAssigned() {
        Minecraft.getInstance().execute(() -> {
            if (!multiplayerActive || announceReady) return;
            announceReady = true;
            announceTo(WorldHostFriends.all());
        });
    }

    private static void announceTo(java.util.Collection<UUID> friends) {
        if (multiplayerActive && announceReady && client != null && !client.isClosed()) {
            client.publishedWorld(friends);
        }
    }

    /** True while the current integrated server is shared to friends through the relay. */
    public static boolean isMultiplayerActive() {
        return multiplayerActive;
    }

    private static void onFriendListChanged() {
        Minecraft.getInstance().execute(() -> {
            ONLINE_FRIENDS.keySet().removeIf(id -> !WorldHostFriends.isFriend(id));
            if (client == null || client.isClosed()) return;
            client.listOnline(WorldHostFriends.all());
            announceTo(WorldHostFriends.all());
        });
    }

    public static void tick() {
        if (!initialized) return;
        if (!friendListenerRegistered) {
            PlayerSocialManager social = Minecraft.getInstance().getPlayerSocialManager();
            if (social != null) {
                social.addFriendListUpdateListener(WorldHostManager::onFriendListChanged);
                friendListenerRegistered = true;
            }
        }
        if (multiplayerActive && Minecraft.getInstance().getSingleplayerServer() == null) {
            onWorldUnpublished(); // left the world without an unpublish callback
        }
        if (pendingQueryFriend != null && --pendingQueryTicks <= 0) {
            UUID friend = pendingQueryFriend;
            pendingQueryFriend = null;
            LOGGER.info("[join] No e4mc domain from {} in time, falling back to proxy join", friend);
            showToast("Joining...", WorldHostFriends.nameOf(friend) + " didn't send an e4mc address (old version?). Trying the relay.");
            proxyJoin(friend);
        }
        if (multiplayerActive && !announceReady && --announceTimeoutTicks <= 0) {
            announceReady = true; // e4mc gave no domain in time: friends still join via the relay proxy
            announceTo(WorldHostFriends.all());
        }
        if (client == null || client.isClosed()) {
            client = null;
            if (reconnectDelayTicks > 0) {
                reconnectDelayTicks--;
                return;
            }
            reconnectDelayTicks = 200; // ~10s between reconnect attempts
            connect();
        }
        if (hostBridge != null) {
            hostBridge.tick();
        }
    }

    private static void connect() {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        if (user == null) return;
        client = new WHProtocolClient(
                DEFAULT_HOST, WHProtocolClient.DEFAULT_PORT, user,
                WorldHostManager::handleMessage,
                () -> {
                    // Relay socket died (reset/EOF/etc). Any join we were mid-attempt on is now
                    // pointed at a dead relay session; drop it so a stale OnlineGame can't fire
                    // ConnectScreen against a proxy port the relay already tore down.
                    attemptingToJoin = null;
                    pendingQueryFriend = null;
                }
        );
        client.getConnectedFuture().thenRun(() -> {
            if (client != null) {
                LOGGER.info("Connected to World Host relay {}:{}", DEFAULT_HOST, WHProtocolClient.DEFAULT_PORT);
                client.listOnline(WorldHostFriends.all());
                if (hostBridge != null) {
                    hostBridge.rebind(client);
                }
                announceTo(WorldHostFriends.all());
            }
        });
    }

    public static boolean isConnected() {
        return client != null && !client.isClosed();
    }

    // --- friends (vanilla friend list) ---------------------------------------

    public static boolean isFriendOnline(UUID uuid) {
        return ONLINE_FRIENDS.containsKey(uuid);
    }

    // --- publishing your world ---------------------------------------------

    /** Call after IntegratedServer.publishServer(...) succeeds, to announce it to online friends. */
    public static void onWorldPublished() {
        Mode mode = pendingMode;
        pendingMode = Mode.LAN;
        if (mode != Mode.MULTIPLAYER) return; // plain LAN: never announced
        multiplayerActive = true;
        announceReady = E4mcDomainHolder.get() != null; // otherwise wait for e4mc's domain
        announceTimeoutTicks = ANNOUNCE_TIMEOUT_TICKS;
        LOGGER.info("World published, waiting for e4mc domain before announcing to {} friend(s)", WorldHostFriends.all().size());
        if (hostBridge == null) {
            hostBridge = new ProxyHostBridge(client);
        } else {
            hostBridge.rebind(client);
        }
    }

    public static void onWorldUnpublished() {
        if (!multiplayerActive) return;
        multiplayerActive = false;
        announceReady = false;
        E4mcDomainHolder.clear();
        if (client != null) {
            client.closedWorld(WorldHostFriends.all());
        }
        if (hostBridge != null) {
            hostBridge.close();
            hostBridge = null;
        }
    }

    // --- message handling ----------------------------------------------------

    private static void handleMessage(WHS2CMessage message) {
        Minecraft.getInstance().execute(() -> handleMessageOnMainThread(message));
    }

    private static void handleMessageOnMainThread(WHS2CMessage message) {
        switch (message) {
            case WHS2CMessage.Error error -> LOGGER.warn("World Host server error: {}", error.message());
            case WHS2CMessage.Warning warning -> LOGGER.warn("World Host server warning: {}", warning.message());

            case WHS2CMessage.OnlineGame game -> {
                LOGGER.info("[join] OnlineGame {}:{} owner={} (attempting={})", game.host(), game.port(), game.ownerCid(), attemptingToJoin);
                if (attemptingToJoin == null || game.ownerCid() != attemptingToJoin) break;
                attemptingToJoin = null;
                Minecraft mc = Minecraft.getInstance();
                ServerData serverData = new ServerData("Friend's world", game.host() + ":" + game.port(), ServerData.Type.OTHER);
                ConnectScreen.startConnecting(
                        mc.gui.screen() != null ? mc.gui.screen() : new TitleScreen(),
                        mc, new ServerAddress(game.host(), game.port()), serverData, false, null
                );
            }

            case WHS2CMessage.FriendRequest req -> {
                // A friend came online: (re)announce our world to just them.
                if (WorldHostFriends.isFriend(req.fromUser())) {
                    announceTo(Set.of(req.fromUser()));
                }
            }

            case WHS2CMessage.PublishedWorld pub -> {
                if (!WorldHostFriends.isFriend(pub.user())) break;
                ONLINE_FRIENDS.put(pub.user(), pub.connectionId());
                showToast(
                        "Friend's world is open",
                        WorldHostFriends.nameOf(pub.user()) + " opened their world. Open your Friends list to join."
                );
            }

            case WHS2CMessage.ClosedWorld closed -> ONLINE_FRIENDS.remove(closed.user());

            case WHS2CMessage.RequestJoin reqJoin -> {
                // A friend wants to join OUR world.
                if (!multiplayerActive || client == null || !WorldHostFriends.isFriend(reqJoin.user())) break;
                LOGGER.info("[join] RequestJoin from {} cid={} -> granting (e4mc domain={})", reqJoin.user(), reqJoin.connectionId(), E4mcDomainHolder.get());
                client.joinGranted(reqJoin.connectionId());
                showToast("Join request", WorldHostFriends.nameOf(reqJoin.user()) + " is joining your world.");
            }

            case WHS2CMessage.ProxyConnect connect -> {
                LOGGER.info("[join] ProxyConnect cid={} from {} (bridge={})", connect.connectionId(), connect.remoteAddr(), hostBridge != null);
                if (hostBridge != null) hostBridge.onProxyConnect(connect.connectionId(), connect.remoteAddr());
            }
            case WHS2CMessage.ProxyC2SPacket packet -> {
                if (hostBridge != null) hostBridge.onProxyData(packet.connectionId(), packet.data());
            }
            case WHS2CMessage.ProxyDisconnect disconnect -> {
                if (hostBridge != null) hostBridge.onProxyDisconnect(disconnect.connectionId());
            }

            case WHS2CMessage.QueryRequest query -> {
                // A friend wants our address. Answer with our e4mc domain hidden in the MOTD.
                String domain = E4mcDomainHolder.get();
                LOGGER.info("[join] QueryRequest from {} cid={} (domain={})", query.friend(), query.connectionId(), domain);
                if (!multiplayerActive || client == null || domain == null || domain.isEmpty()) break;
                if (!WorldHostFriends.isFriend(query.friend())) break;
                client.queryResponse(query.connectionId(), encodeStatus(DOMAIN_MARKER + domain));
            }

            case WHS2CMessage.NewQueryResponse response -> {
                LOGGER.info("[join] NewQueryResponse from {} ({} bytes, pending={})", response.friend(), response.status().length, pendingQueryFriend);
                if (pendingQueryFriend == null || !pendingQueryFriend.equals(response.friend())) break;
                String domain = decodeDomain(response.status());
                LOGGER.info("[join] Query answer from {}: domain={}", response.friend(), domain);
                if (domain == null) break; // not a Minimals host (or no domain yet): wait for timeout fallback
                pendingQueryFriend = null;
                Minecraft mc = Minecraft.getInstance();
                ServerData serverData = new ServerData("Friend's world (e4mc)", domain, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(
                        mc.gui.screen() != null ? mc.gui.screen() : new TitleScreen(),
                        mc, ServerAddress.parseString(domain), serverData, false, null
                );
            }

            case WHS2CMessage.ConnectionNotFound notFound ->
                    LOGGER.warn("World Host: connection {} not found", notFound.connectionId());

            default -> { }
        }
    }

    /**
     * Asks the relay to open a proxy connection to a friend's published world. The relay
     * answers with an OnlineGame message (handled above), which is what actually starts
     * the Minecraft connection.
     */
    public static void connectToFriend(UUID friendUuid) {
        if (!ONLINE_FRIENDS.containsKey(friendUuid)) return;
        if (client == null) {
            showToast("Can't join yet", "Still connecting to World Host, try again in a moment.");
            return;
        }
        // Ask the host for its e4mc domain (the relay only forwards this one small message),
        // then connect straight to e4mc. No game traffic goes through World Host.
        if (friendUuid.equals(pendingQueryFriend)) return; // already asking; repeated clicks must not reset the timer
        pendingQueryFriend = friendUuid;
        pendingQueryTicks = QUERY_TIMEOUT_TICKS;
        LOGGER.info("[join] Querying {} for their e4mc domain", friendUuid);
        showToast("Joining...", "Asking " + WorldHostFriends.nameOf(friendUuid) + " for their address.");
        client.queryFriends(List.of(friendUuid));
    }

    /** Old path, kept only as a fallback for hosts that don't answer with an e4mc domain. */
    private static void proxyJoin(UUID friendUuid) {
        Long connectionId = ONLINE_FRIENDS.get(friendUuid);
        if (connectionId == null || client == null) return;
        attemptingToJoin = connectionId;
        client.requestDirectJoin(connectionId);
    }

    private static byte[] encodeStatus(String motd) {
        ServerStatus status = new ServerStatus(Component.literal(motd), Optional.empty(), Optional.empty(), Optional.empty(), false);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundStatusResponsePacket.STREAM_CODEC.encode(buf, new ClientboundStatusResponsePacket(status));
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /** Returns the e4mc domain hidden in a status' MOTD, or null if absent/invalid. */
    private static String decodeDomain(byte[] statusBytes) {
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(statusBytes));
            String motd = ClientboundStatusResponsePacket.STREAM_CODEC.decode(buf).status().description().getString();
            if (!motd.startsWith(DOMAIN_MARKER)) return null;
            String domain = motd.substring(DOMAIN_MARKER.length()).trim().toLowerCase(java.util.Locale.ROOT);
            // Only a plain hostname: the value comes from another player, so keep it strict.
            if (domain.isEmpty() || domain.length() > 253 || !domain.matches("[a-z0-9]([a-z0-9.-]*[a-z0-9])?")) return null;
            return domain;
        } catch (Exception e) {
            LOGGER.warn("[join] Could not read query answer", e);
            return null;
        }
    }

    // --- helpers ---------------------------------------------------------

    private static void showToast(String title, String description) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.add(
                mc.gui.toastManager(),
                new SystemToast.SystemToastId(),
                Component.literal(title).withStyle(ChatFormatting.AQUA),
                Component.literal(description)
        );
    }
}
