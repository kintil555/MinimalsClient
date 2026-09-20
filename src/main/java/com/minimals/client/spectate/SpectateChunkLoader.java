package com.minimals.client.spectate;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.UUID;

/**
 * Makes the target's chunk exist for the spectator.
 *
 * The client rejects any chunk outside {@code viewCenter +- radius}, and the server only
 * sends chunks near OUR body. So for the camera to see the target's surroundings we must
 *   1. keep the chunk loaded on the server (ticket),
 *   2. move the client's chunk view centre onto the target's chunk,
 *   3. push that chunk to our own connection.
 * This is possible only in singleplayer, where the integrated server lives in this process.
 * On a remote server the camera still follows the target, but only chunks already inside
 * our own view range are visible (a client cannot make a remote server send more).
 */
public final class SpectateChunkLoader {

    /** Chunks kept loaded around the target: 1 = the target chunk plus its 8 neighbours. */
    private static final int RADIUS = 1;

    private static ServerLevel ticketLevel;
    private static ChunkPos ticketPos;
    /** True while the client view centre sits on the target instead of our body. */
    private static boolean centred;

    private SpectateChunkLoader() {
    }

    public static void request(UUID targetId) {
        // Ticket/centre are placed in tick(), once the target's position is known.
    }

    public static void tick(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = SpectateManager.targetId();
        if (server == null || id == null || mc.player == null) {
            return;
        }
        UUID selfId = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(id);
            ServerPlayer self = server.getPlayerList().getPlayer(selfId);
            if (target == null || self == null) {
                return;
            }
            ServerLevel level = target.level();
            ChunkPos pos = target.chunkPosition();
            if (level != self.level()) {
                return; // other dimension: cannot be shown through our own connection
            }
            if (level == ticketLevel && pos.equals(ticketPos)) {
                return;
            }
            releaseTicket();
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, pos, RADIUS);
            ticketLevel = level;
            ticketPos = pos;
            sendAround(self, level, pos, RADIUS);
            centred = true;
        });
    }

    /** Server thread: centre the client on {@code pos} and send it the chunks around it. */
    private static void sendAround(ServerPlayer self, ServerLevel level, ChunkPos pos, int radius) {
        self.connection.send(new ClientboundSetChunkCacheCenterPacket(pos.x(), pos.z()));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x() + dx, pos.z() + dz);
                if (chunk != null) {
                    self.connection.send(new ClientboundLevelChunkWithLightPacket(
                            chunk, level.getLightEngine(), null, null));
                }
            }
        }
    }

    public static void release() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            ticketLevel = null;
            ticketPos = null;
            centred = false;
            return;
        }
        UUID selfId = mc.player == null ? null : mc.player.getUUID();
        server.execute(() -> {
            releaseTicket();
            // Put the client view back on our own body and resend its nearby chunks.
            if (selfId != null && centred) {
                ServerPlayer self = server.getPlayerList().getPlayer(selfId);
                if (self != null) {
                    sendAround(self, self.level(), self.chunkPosition(), 2);
                }
            }
            centred = false;
        });
    }

    private static void releaseTicket() {
        if (ticketLevel != null && ticketPos != null) {
            ticketLevel.getChunkSource().removeTicketWithRadius(TicketType.FORCED, ticketPos, RADIUS);
        }
        ticketLevel = null;
        ticketPos = null;
    }
}
