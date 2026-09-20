package com.minimals.client.replay;

import com.minimals.client.MinimalClientMod;
import com.minimals.client.mixin.MinecraftAccessor;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.local.LocalAddress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays a .mreplay file in a world of its own.
 *
 * There is no server. A LocalServerChannel that drops everything the client sends stands in for
 * one, and the recorded clientbound frames are pushed into the client pipeline by hand, on the
 * client tick. The vanilla listeners (login -> configuration -> play) run unchanged: they build the
 * ClientLevel and LocalPlayer from the recorded packets, and their replies are simply discarded.
 *
 * The whole file is loaded into memory as frames, so seeking is: drop the world, rejoin, and feed
 * every frame up to the target tick in one go.
 */
public final class ReplayPlayer {

    private static final List<ReplayFormat.Frame> FRAMES = new ArrayList<>();

    private static volatile boolean active;
    private static Connection connection;
    private static Channel serverChannel;
    private static Channel clientChannel;
    private static ChannelFuture serverBind;

    private static int totalTicks;
    /** Timeline position in ticks (fractional, so slow speeds stay smooth). */
    private static double position;
    private static int nextFrame;
    private static boolean paused;
    private static double speed = 1.0;
    private static String name = "";
    /** Target of an in-flight seek: frames are fed without pacing until this tick is reached. */
    private static int seekTarget = -1;
    private static boolean pendingSeek;
    /** True once the first recorded position packet has placed the camera in this world. */
    private static boolean placed;
    /** Where the player stood when Record was pressed, or null for pose-less files. */
    private static ReplayFormat.Pose startPose;
    /** True once the camera has been moved to {@link #startPose} in the current world. */
    private static boolean poseApplied;
    /** Camera position kept across a seek (which rebuilds the world), so flying is not undone. */
    private static ReplayFormat.Pose keptPose;
    /** The timeline bar is opened once per world so the user sees the controls, then may close it. */
    private static boolean timelineShown;

    private ReplayPlayer() {
    }

    // ---- state ----------------------------------------------------------------------------

    public static boolean isActive() {
        return active;
    }

    /**
     * Every CLIENTBOUND connection created while a replay is starting or running is the playback
     * connection: the recorder must not tap it. {@code connection} is assigned only after
     * connectToLocalServer returns, which is after the pipeline (and tap) has been built, so the
     * test cannot compare against it.
     */
    public static boolean isPlaybackConnection(Connection c) {
        return active;
    }

    public static int totalTicks() {
        return totalTicks;
    }

    public static int positionTicks() {
        return (int) position;
    }

    /** Whether a recorded teleport should be applied: only the first one per world. */
    public static boolean consumePlacement() {
        if (placed) {
            return false;
        }
        placed = true;
        // With a recorded start pose the join teleport must not move the camera: that packet holds
        // where the player JOINED, often underground relative to the chunk, not where Record began.
        return startPose == null && keptPose == null;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void setPaused(boolean p) {
        paused = p;
    }

    public static double speed() {
        return speed;
    }

    public static void setSpeed(double s) {
        speed = Math.max(0.05, Math.min(16.0, s));
    }

    public static String name() {
        return name;
    }

    /** True once the recorded play-state world is on screen and the timeline can be shown. */
    public static boolean isInWorld() {
        Minecraft mc = Minecraft.getInstance();
        return active && mc.level != null && mc.player != null;
    }

    // ---- open / close ---------------------------------------------------------------------

    public static void open(Path file) {
        Minecraft mc = Minecraft.getInstance();
        List<ReplayFormat.Frame> loaded = new ArrayList<>();
        int last = 0;
        String replayName;
        ReplayFormat.Pose pose;
        try (ReplayFormat.Reader r = new ReplayFormat.Reader(file)) {
            replayName = r.header().name();
            pose = r.pose();
            ReplayFormat.Frame f;
            while ((f = r.next()) != null) {
                loaded.add(f);
                last = Math.max(last, f.tick());
            }
        } catch (IOException e) {
            MinimalClientMod.LOGGER.warn("Cannot open replay {}", file, e);
            return;
        }
        if (loaded.isEmpty()) {
            return;
        }
        FRAMES.clear();
        FRAMES.addAll(loaded);
        totalTicks = last;
        name = replayName;
        startPose = pose;
        paused = false;
        speed = 1.0;
        begin(mc, 0);
    }

    /** (Re)creates the world and feeds frames up to {@code targetTick} immediately. */
    private static void begin(Minecraft mc, int targetTick) {
        keptPose = isInWorld() && poseApplied
                ? new ReplayFormat.Pose(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        mc.player.getYRot(), mc.player.getXRot())
                : null;
        shutdownConnection();
        // Leave whatever world we are in (or the title screen) before building the replay world.
        mc.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(Component.literal("Loading replay")), false);
        active = true;
        position = 0;
        nextFrame = 0;
        seekTarget = targetTick;
        placed = false;
        poseApplied = false;
        timelineShown = false;

        LevelLoadTracker tracker = new LevelLoadTracker(0L);
        mc.gui.setScreen(new LevelLoadingScreen(tracker, LevelLoadingScreen.Reason.OTHER));

        try {
            SocketAddress address = startFakeServer();
            Connection c = Connection.connectToLocalServer(address);
            connection = c;
            c.initiateServerboundPlayConnection(address.toString(), 0,
                    new ClientHandshakePacketListenerImpl(c, mc, null, null, false, Duration.ZERO, x -> { }, tracker, null));
            c.send(new ServerboundHelloPacket(mc.getUser().getName(), mc.getUser().getProfileId()));
            ((MinecraftAccessor) mc).minimals$setPendingConnection(c);
        } catch (RuntimeException e) {
            MinimalClientMod.LOGGER.error("Replay start failed", e);
            close();
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        boolean wasActive = active;
        active = false;
        shutdownConnection();
        FRAMES.clear();
        startPose = null;
        keptPose = null;
        if (wasActive) {
            mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
        }
    }

    private static void shutdownConnection() {
        Connection c = connection;
        connection = null;
        if (c != null && c.isConnected()) {
            c.disconnect(Component.literal("Replay closed"));
        }
        if (serverChannel != null) {
            serverChannel.close();
            serverChannel = null;
        }
        if (serverBind != null) {
            serverBind.channel().close();
            serverBind = null;
        }
        clientChannel = null;
    }

    /** Server end of the in-memory pipe: accepts the client and discards every byte it sends. */
    private static SocketAddress startFakeServer() {
        serverBind = new ServerBootstrap()
                .channel(EventLoopGroupHolder.local().serverChannelCls())
                .group(EventLoopGroupHolder.local().eventLoopGroup())
                .localAddress(LocalAddress.ANY)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        serverChannel = ch;
                        ch.pipeline().addLast("minimals_sink", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                io.netty.util.ReferenceCountUtil.release(msg);
                            }
                        });
                    }
                })
                .bind()
                .syncUninterruptibly();
        return serverBind.channel().localAddress();
    }

    /** Camera height above the recorded player's feet: a little over eye level (1.62). */
    private static final double CAMERA_LIFT = 2.0;

    /**
     * Moves the free camera to where the player was when Record was pressed, a little above head
     * height. Runs once per world, after the join teleport has been consumed, so nothing overwrites
     * it. Files without a pose keep the position the recorded join packet gave.
     */
    private static void applyStartPose(Minecraft mc) {
        poseApplied = true;
        ReplayFormat.Pose kept = keptPose;
        keptPose = null;
        ReplayFormat.Pose pose = kept != null ? kept : startPose;
        if (pose == null || mc.player == null) {
            return;
        }
        double lift = kept != null ? 0.0 : CAMERA_LIFT;
        mc.player.setPos(pose.x(), pose.y() + lift, pose.z());
        mc.player.setYRot(pose.yRot());
        mc.player.setXRot(pose.xRot());
        mc.player.setOldPosAndRot();
        mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }

    // ---- timeline control -----------------------------------------------------------------

    /** Jumps to {@code tick}. Rebuilds the world, so it takes a moment. */
    public static void seek(int tick) {
        if (!active) {
            return;
        }
        int target = Math.max(0, Math.min(totalTicks, tick));
        // Going forward inside the already-built world only needs more frames.
        if (target >= position && isInWorld()) {
            seekTarget = target;
            return;
        }
        pendingSeek = true;
        seekTarget = target;
    }

    // ---- per-tick pump --------------------------------------------------------------------

    /** Called from END_CLIENT_TICK. */
    public static void tick() {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (pendingSeek) {
            pendingSeek = false;
            int target = seekTarget;
            begin(mc, target);
            return;
        }
        Connection c = connection;
        if (c == null || !c.isConnected()) {
            if (isInWorld()) {
                close();
            }
            return;
        }
        Channel ch = channel(c);
        if (ch == null) {
            return;
        }

        if (isInWorld() && !poseApplied) {
            applyStartPose(mc);
        }

        if (isInWorld() && !timelineShown && mc.gui.screen() == null) {
            timelineShown = true;
            mc.gui.setScreen(new TimelineScreen());
        }

        if (!isInWorld()) {
            // Login / configuration / initial play: deliver the setup frames (tick 0) so the world
            // can be built. The timeline does not run until the world exists, or the recorded ticks
            // would elapse behind the loading screen.
            feedUntil(ch, 0, 200);
            return;
        }
        if (seekTarget >= 0 && position < seekTarget) {
            // Fast-forward to the seek target without pacing, then resume normal playback.
            feedUntil(ch, seekTarget, 4000);
            if (nextFrame >= FRAMES.size() || FRAMES.get(nextFrame).tick() > seekTarget) {
                position = seekTarget;
                seekTarget = -1;
            }
            return;
        }
        seekTarget = -1;
        if (paused) {
            return;
        }
        position = Math.min(totalTicks, position + speed);
        feedUntil(ch, (int) position, 4000);
        if (position >= totalTicks && nextFrame >= FRAMES.size()) {
            paused = true;
        }
    }

    /**
     * Injects frames whose tick is &lt;= {@code untilTick}, at most {@code budget} per call.
     *
     * Vanilla swaps protocols in two steps: a terminal packet (login finished, finish
     * configuration) makes the decoder replace itself with "inbound_config" and stop auto-read, and
     * only after the MAIN thread has handled that packet is the new decoder installed. A frame that
     * arrives in between makes "inbound_config" throw. So a batch never crosses a protocol boundary,
     * and nothing is injected while the pipeline is not fully configured; the remaining frames go
     * in on a later client tick, once the main thread has swapped the listener.
     */
    private static void feedUntil(Channel ch, int untilTick, int budget) {
        if (!pipelineReady(ch)) {
            return;
        }
        List<byte[]> batch = new ArrayList<>();
        int consumed = 0;
        while (nextFrame + consumed < FRAMES.size() && consumed < budget) {
            ReplayFormat.Frame f = FRAMES.get(nextFrame + consumed);
            if (f.tick() > untilTick) {
                break;
            }
            batch.add(f.data());
            consumed++;
            int following = nextFrame + consumed;
            if (following < FRAMES.size() && FRAMES.get(following).protocol() != f.protocol()) {
                break;
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        nextFrame += consumed;
        ByteBuf[] bufs = new ByteBuf[batch.size()];
        for (int i = 0; i < bufs.length; i++) {
            bufs[i] = Unpooled.wrappedBuffer(batch.get(i));
        }
        ch.eventLoop().execute(() -> {
            ChannelHandlerContext ctx = ch.pipeline().context("splitter");
            for (ByteBuf b : bufs) {
                if (ctx == null || !ch.isOpen()) {
                    b.release();
                } else {
                    ctx.fireChannelRead(b);
                }
            }
        });
    }

    /** True while the pipeline has a real decoder (not the transient "inbound_config" placeholder). */
    private static boolean pipelineReady(Channel ch) {
        return ch.pipeline().get("decoder") != null && ch.config().isAutoRead();
    }

    private static Channel channel(Connection c) {
        if (clientChannel == null) {
            clientChannel = ((com.minimals.client.mixin.ConnectionAccessor) c).minimals$getChannel();
        }
        return clientChannel;
    }

    /** Draws nothing; kept so screens can ask whether a replay owns the screen. */
    public static boolean ownsScreen(Screen screen) {
        return active && screen instanceof LevelLoadingScreen;
    }
}
