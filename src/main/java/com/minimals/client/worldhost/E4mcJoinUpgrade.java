package com.minimals.client.worldhost;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.level.ServerPlayer;

/**
 * Wires up E4mcDomainPayload: registers the codec both directions, sends it from the
 * host to a friend right after they finish joining through the World Host relay proxy,
 * and on the joiner's side disconnects from the proxy and silently reconnects over the
 * real e4mc/iroh P2P domain instead. If e4mc isn't installed or never assigns a domain,
 * onWorldPublished() below just never sends this payload, and the joiner stays on the
 * relay proxy connection they already have — that's the fallback, not a separate path.
 */
public final class E4mcJoinUpgrade {
    private E4mcJoinUpgrade() {}

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(E4mcDomainPayload.TYPE, E4mcDomainPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(E4mcDomainPayload.TYPE, E4mcDomainPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(E4mcDomainPayload.TYPE, (payload, context) ->
                context.client().execute(() -> onDomainReceived(payload.domain())));

        // Fires for every player joining OUR integrated server, whether they came in via
        // the World Host relay proxy or (in singleplayer/LAN-open cases) directly. Only the
        // host process runs this listener meaningfully, since only the host has a running
        // integrated server; on a pure client with no server this event simply never fires.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendDomainTo(handler.player));

        // Drop the captured domain when the server stops; e4mc assigns a fresh one next session.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                E4mcDomainHolder.clear());
    }

    /** Call from the host, once a friend's ServerPlayer finishes joining via the proxy. */
    public static void sendDomainTo(ServerPlayer player) {
        String domain = E4mcDomainHolder.get();
        if (domain == null || domain.isEmpty()) return; // e4mc not running / no domain yet: stay on relay
        ServerPlayNetworking.send(player, new E4mcDomainPayload(domain));
    }

    private static void onDomainReceived(String domain) {
        Minecraft mc = Minecraft.getInstance();
        ServerAddress address;
        try {
            address = ServerAddress.parseString(domain);
        } catch (Exception e) {
            // Malformed domain (e4mc protocol drifted, etc): stay on the relay connection.
            return;
        }
        ServerData serverData = new ServerData("Friend's world (P2P)", domain, ServerData.Type.OTHER);
        // Disconnect from the relay-proxied connection first, then reconnect over e4mc.
        // A brief visible reconnect is an acceptable trade for not silently swapping sockets
        // under the game's active connection. Uses Minecraft's own disconnect() rather than
        // touching level/connection fields directly, since that path handles world-save,
        // GUI teardown, etc. that a raw Connection#disconnect call would skip.
        TitleScreen titleScreen = new TitleScreen();
        mc.disconnect(titleScreen, false);
        ConnectScreen.startConnecting(titleScreen, mc, address, serverData, false, null);
    }
}
