package com.minimals.client.replay;

import com.minimals.client.MinimalClientMod;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Auto-buffer recorder.
 *
 * From the moment a connection is created every clientbound frame is streamed to a scratch file
 * ({@link ReplayStorage#bufferFile()}). That makes the recording self-contained: it always starts
 * with the login/configuration frames and the full state the server sent on join, so it can be
 * replayed from scratch.
 *
 * The Record button only marks a start tick. Stopping finalises a clip: the scratch file is
 * copied to a {@code .mreplay} whose frames before the mark keep tick 0 (instant setup) and whose
 * later frames are rebased to start at 0 at the mark.
 */
public final class ReplayRecorder {

    private static final Object LOCK = new Object();
    private static final Object POISON = new Object();
    /** Asks the writer thread to flush and then release the given latch. */
    private record Flush(java.util.concurrent.CountDownLatch done) {
    }

    /** Client ticks since the current connection was created. */
    private static volatile int tick;
    private static volatile boolean recording;
    private static volatile int markTick;
    private static volatile String status = "";
    /** Where the player stood when Record was pressed; becomes the replay's starting camera. */
    private static volatile ReplayFormat.Pose markPose;

    private static Connection current;
    private static LinkedBlockingQueue<Object> queue;
    private static Thread writerThread;
    private static volatile ReplayFormat.Writer writer;
    /** Wall clock of the first frame, for the header. */
    private static long sessionStart;

    private ReplayRecorder() {
    }

    public static boolean isRecording() {
        return recording;
    }

    public static String status() {
        return status;
    }

    public static int recordedTicks() {
        return recording ? tick - markTick : 0;
    }

    /** True when a live play-state connection exists to record. */
    public static boolean canRecord() {
        Minecraft mc = Minecraft.getInstance();
        return !ReplayPlayer.isActive() && mc.level != null && mc.getConnection() != null && queue != null;
    }

    /** Client tick (called from END_CLIENT_TICK). */
    public static void tick() {
        tick++;
        sampleLocalPlayer();
    }

    /** Last LocalPlayer whose profile frame was written (a new object = respawn / new world). */
    private static java.lang.ref.WeakReference<net.minecraft.client.player.LocalPlayer> sampledPlayer;

    /**
     * Packets never describe the recording player to itself, so its movement is stored separately:
     * a profile frame whenever the LocalPlayer object changes and one small sample per tick. On
     * playback a client-side RemotePlayer follows these (see ReplayRemotePlayer).
     */
    private static void sampleLocalPlayer() {
        LinkedBlockingQueue<Object> q = queue;
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (q == null || player == null || mc.level == null || ReplayPlayer.isActive()) {
            return;
        }
        try {
            if (sampledPlayer == null || sampledPlayer.get() != player) {
                sampledPlayer = new java.lang.ref.WeakReference<>(player);
                q.add(new ReplayFormat.Frame(ReplayFormat.PROTO_LOCAL, tick,
                        ReplayLocalTrack.encodeProfile(player, mc.getGameProfile(), mc.level.registryAccess())));
            }
            q.add(new ReplayFormat.Frame(ReplayFormat.PROTO_LOCAL, tick, ReplayLocalTrack.encodeSample(player)));
        } catch (RuntimeException e) {
            MinimalClientMod.LOGGER.debug("Local player sample skipped", e);
        }
    }

    // ---- connection lifecycle -------------------------------------------------------------

    public static void installTap(Connection connection, ChannelPipeline pipeline) {
        // The playback connection has no recorder: replays must never re-record themselves.
        if (ReplayPlayer.isPlaybackConnection(connection)) {
            return;
        }
        if (pipeline.get("minimals_tap") != null || pipeline.get("decoder") == null && pipeline.get("inbound_config") == null) {
            return;
        }
        String anchor = pipeline.get("decoder") != null ? "decoder" : "inbound_config";
        synchronized (LOCK) {
            endSession("New connection");
            current = connection;
            tick = 0;
            markTick = 0;
            sessionStart = System.currentTimeMillis();
            queue = new LinkedBlockingQueue<>();
            try {
                writer = new ReplayFormat.Writer(ReplayStorage.bufferFile(), "buffer",
                        SharedConstants.getCurrentVersion().name(), sessionStart);
            } catch (IOException e) {
                MinimalClientMod.LOGGER.warn("Replay buffer unavailable", e);
                queue = null;
                return;
            }
            final LinkedBlockingQueue<Object> q = queue;
            final ReplayFormat.Writer w = writer;
            writerThread = new Thread(() -> runWriter(q, w), "Minimals-ReplayWriter");
            writerThread.setDaemon(true);
            writerThread.start();
        }
        pipeline.addBefore(anchor, "minimals_tap", new Tap(connection));
    }

    /** Closes the buffer of the previous session. Recording in progress is finalised first. */
    private static void endSession(String reason) {
        if (recording) {
            recording = false;
            status = reason;
        }
        if (queue != null) {
            queue.add(POISON);
            try {
                if (writerThread != null) {
                    writerThread.join(3000);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            queue = null;
        }
    }

    /**
     * Called when the game leaves a world. If a clip was being recorded it is saved before the
     * buffer is discarded.
     */
    public static void onDisconnect() {
        synchronized (LOCK) {
            if (recording) {
                stopInternal("Saved (disconnected)");
            }
            endSession("");
            current = null;
        }
    }

    // ---- user actions ---------------------------------------------------------------------

    public static void start() {
        synchronized (LOCK) {
            if (recording || !canRecord()) {
                return;
            }
            markTick = tick;
            markPose = capturePose();
            recording = true;
            status = "Recording";
        }
    }

    /** Saves the clip from the mark to now as a .mreplay file. */
    public static void stop() {
        synchronized (LOCK) {
            if (!recording) {
                return;
            }
            stopInternal("Saved");
        }
    }

    private static void stopInternal(String reason) {
        recording = false;
        int endTick = tick;
        int from = markTick;
        flushBuffer();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        Path out = ReplayStorage.dir().resolve(stamp + ReplayStorage.EXTENSION);
        try {
            // Read a copy: the live buffer keeps being written while we clip it.
            Path snapshot = ReplayStorage.dir().resolve(".snapshot.tmp");
            Files.copy(ReplayStorage.bufferFile(), snapshot, StandardCopyOption.REPLACE_EXISTING);
            try {
                clip(snapshot, out, stamp, from, endTick, markPose);
            } finally {
                Files.deleteIfExists(snapshot);
            }
            status = reason + ": " + out.getFileName();
            MinimalClientMod.LOGGER.info("Replay saved: {}", out);
        } catch (IOException e) {
            status = "Save failed: " + e.getMessage();
            MinimalClientMod.LOGGER.warn("Replay save failed", e);
        }
    }

    private static ReplayFormat.Pose capturePose() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return new ReplayFormat.Pose(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static void flushBuffer() {
        LinkedBlockingQueue<Object> q = queue;
        if (q == null) {
            return;
        }
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        q.add(new Flush(latch));
        try {
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Copies frames [0..endTick] rebasing ticks so that frames before {@code from} land on 0. */
    private static void clip(Path buffer, Path out, String name, int from, int endTick,
                             ReplayFormat.Pose pose) throws IOException {
        try (ReplayFormat.Reader in = new ReplayFormat.Reader(buffer);
             ReplayFormat.Writer w = new ReplayFormat.Writer(out, name,
                     in.header().mcVersion(), in.header().startedAt(), pose)) {
            ReplayFormat.Frame f;
            while ((f = in.next()) != null) {
                if (f.tick() > endTick) {
                    break;
                }
                if (f.protocol() == ReplayFormat.PROTO_LOCAL && f.data().length > 0
                        && f.data()[0] == ReplayLocalTrack.KIND_SAMPLE && f.tick() < from - 1) {
                    continue; // only the last pre-mark sample is needed to place the model at start
                }
                w.write(f.protocol(), Math.max(0, f.tick() - from), f.data());
            }
        }
    }

    // ---- writer ---------------------------------------------------------------------------

    private static void runWriter(LinkedBlockingQueue<Object> q, ReplayFormat.Writer w) {
        try (w) {
            while (true) {
                Object o = q.take();
                if (o == POISON) {
                    break;
                }
                if (o instanceof Flush flush) {
                    w.flush();
                    flush.done().countDown();
                    continue;
                }
                ReplayFormat.Frame f = (ReplayFormat.Frame) o;
                w.write(f.protocol(), f.tick(), f.data());
            }
        } catch (IOException e) {
            status = "Buffer write failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- tap ------------------------------------------------------------------------------

    /**
     * Runs on the netty thread in front of the vanilla decoder, so it sees the raw, decrypted and
     * decompressed frame. The protocol is read from the connection's current listener, which is
     * switched by the packet handler BEFORE the next frame is decoded (frames are one per read
     * thanks to FlowControlHandler).
     */
    private static final class Tap extends ChannelInboundHandlerAdapter {
        private final Connection connection;

        Tap(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf buf && buf.isReadable()) {
                byte proto = protocolOf();
                LinkedBlockingQueue<Object> q = queue;
                if (proto >= 0 && q != null) {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.getBytes(buf.readerIndex(), data);
                    q.add(new ReplayFormat.Frame(proto, tick, data));
                }
            }
            super.channelRead(ctx, msg);
        }

        private byte protocolOf() {
            PacketListener listener = connection.getPacketListener();
            if (listener == null) {
                return -1;
            }
            ConnectionProtocol p = listener.protocol();
            if (p == ConnectionProtocol.LOGIN) {
                return ReplayFormat.PROTO_LOGIN;
            } else if (p == ConnectionProtocol.CONFIGURATION) {
                return ReplayFormat.PROTO_CONFIG;
            } else if (p == ConnectionProtocol.PLAY) {
                return ReplayFormat.PROTO_PLAY;
            }
            return -1;
        }
    }
}
