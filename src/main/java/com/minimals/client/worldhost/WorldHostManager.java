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

import java.util.LinkedHashMap;
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
 * World Host protocol v7, talking to the public relay world-host.jemnetworks.com:9646.
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
            if (multiplayerActive) {
                client.publishedWorld(WorldHostFriends.all());
            }
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
                }
        );
        client.getConnectedFuture().thenRun(() -> {
            if (client != null) {
                LOGGER.info("Connected to World Host relay {}:{}", DEFAULT_HOST, WHProtocolClient.DEFAULT_PORT);
                client.listOnline(WorldHostFriends.all());
                if (hostBridge != null) {
                    hostBridge.rebind(client);
                }
                if (multiplayerActive) {
                    client.publishedWorld(WorldHostFriends.all());
                }
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
        LOGGER.info("World published, announcing to {} friend(s)", WorldHostFriends.all().size());
        if (client != null) {
            client.publishedWorld(WorldHostFriends.all());
        }
        if (hostBridge == null) {
            hostBridge = new ProxyHostBridge(client);
        } else {
            hostBridge.rebind(client);
        }
    }

    public static void onWorldUnpublished() {
        if (!multiplayerActive) return;
        multiplayerActive = false;
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
                if (multiplayerActive && client != null && WorldHostFriends.isFriend(req.fromUser())) {
                    client.publishedWorld(Set.of(req.fromUser()));
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
                client.joinGranted(reqJoin.connectionId());
                showToast("Join request", WorldHostFriends.nameOf(reqJoin.user()) + " is joining your world.");
            }

            case WHS2CMessage.ProxyConnect connect -> {
                if (hostBridge != null) hostBridge.onProxyConnect(connect.connectionId(), connect.remoteAddr());
            }
            case WHS2CMessage.ProxyC2SPacket packet -> {
                if (hostBridge != null) hostBridge.onProxyData(packet.connectionId(), packet.data());
            }
            case WHS2CMessage.ProxyDisconnect disconnect -> {
                if (hostBridge != null) hostBridge.onProxyDisconnect(disconnect.connectionId());
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
        Long connectionId = ONLINE_FRIENDS.get(friendUuid);
        if (connectionId == null) return;
        if (client == null) {
            showToast("Can't join yet", "Still connecting to World Host, try again in a moment.");
            return;
        }
        attemptingToJoin = connectionId;
        client.requestDirectJoin(connectionId);
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
