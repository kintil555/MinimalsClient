package com.minimals.client.replay;

import com.minimals.client.mixin.ClientPacketListenerAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The recording player as a normal, visible player model.
 *
 * As in Flashback, the LocalPlayer of a replay is only the free (spectator) camera; the person who
 * made the recording is a separate client-side {@link RemotePlayer} that follows the samples
 * captured by {@link ReplayRecorder}. It has its own id and UUID so it never collides with the
 * LocalPlayer that the recorded login packet created.
 */
public final class ReplayRemotePlayer {

    private static final int FAKE_ID = Integer.MAX_VALUE - 17;

    private static final List<ReplayFormat.Frame> ENTRIES = new ArrayList<>();
    private static int cursor;
    private static byte[] profileBytes;
    private static boolean profileChanged;
    private static ReplayLocalTrack.Sample latest;
    private static boolean latestFresh;
    private static boolean wasSwinging;

    private static ReplayLocalTrack.State state;
    private static boolean stateDirty;
    /** Horizontal distance the recorded player moved in the last applied sample. */
    private static float step;

    private static RemotePlayer entity;
    private static UUID fakeId;

    /** Skin layer bitmask (PlayerModelPart.getMask()) of the recorded player; -1 = unknown. */
    public static int skinLayers() {
        return state == null ? -1 : state.skinLayers();
    }

    /** Per-tick horizontal distance of the recorded player, for the walk animation. */
    public static float stepOf() {
        float s = step;
        step = 0.0F; // consumed: a tick without a new sample (pause, slow speed) must not keep walking
        return s;
    }

    /** True for the entity that stands in for the recorded player. */
    public static boolean isRecordedPlayer(Entity e) {
        return e != null && entity != null && e == entity;
    }

    private ReplayRemotePlayer() {
    }

    public static void load(List<ReplayFormat.Frame> frames) {
        ENTRIES.clear();
        ENTRIES.addAll(frames);
        ENTRIES.sort(Comparator.comparingInt(ReplayFormat.Frame::tick)); // stable
        reset();
    }

    public static void clear() {
        ENTRIES.clear();
        reset();
    }

    /** Called whenever the replay world is rebuilt (start, backwards seek). */
    public static void reset() {
        cursor = 0;
        profileBytes = null;
        profileChanged = false;
        latest = null;
        latestFresh = false;
        wasSwinging = false;
        state = null;
        stateDirty = false;
        step = 0.0F;
        entity = null;
        fakeId = null;
    }

    public static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.getConnection() == null || ENTRIES.isEmpty()) {
            return;
        }
        int pos = ReplayPlayer.positionTicks();
        while (cursor < ENTRIES.size() && ENTRIES.get(cursor).tick() <= pos) {
            byte[] data = ENTRIES.get(cursor++).data();
            if (data.length == 0) {
                continue;
            }
            if (data[0] == ReplayLocalTrack.KIND_PROFILE) {
                profileBytes = data;
                profileChanged = true;
            } else if (data[0] == ReplayLocalTrack.KIND_STATE) {
                ReplayLocalTrack.State st = ReplayLocalTrack.decodeState(data, level.registryAccess());
                if (st != null) {
                    state = st;
                    stateDirty = true;
                }
            } else {
                ReplayLocalTrack.Sample s = ReplayLocalTrack.decodeSample(data);
                if (s != null) {
                    latest = s;
                    latestFresh = true;
                }
            }
        }
        if (latest == null || profileBytes == null) {
            return;
        }
        if (profileChanged || entity == null || entity.isRemoved() || entity.level() != level) {
            spawn(mc, level);
            if (entity == null) {
                return;
            }
        }
        if (stateDirty) {
            stateDirty = false;
            applyState(entity);
        }
        if (!latestFresh) {
            return;
        }
        latestFresh = false;
        apply(entity, latest);
    }

    private static void applyState(RemotePlayer p) {
        if (state == null) {
            return;
        }
        for (int i = 0; i < ReplayLocalTrack.SLOTS.length; i++) {
            EquipmentSlot slot = ReplayLocalTrack.SLOTS[i];
            p.setItemSlot(slot, state.items()[i].copy());
        }
    }

    private static void spawn(Minecraft mc, ClientLevel level) {
        profileChanged = false;
        if (entity != null && !entity.isRemoved()) {
            level.removeEntity(FAKE_ID, Entity.RemovalReason.DISCARDED);
        }
        entity = null;
        GameProfile recorded;
        try {
            recorded = ReplayLocalTrack.decodeProfile(profileBytes, level.registryAccess());
        } catch (RuntimeException e) {
            return;
        }
        UUID real = recorded.id();
        fakeId = new UUID(real.getMostSignificantBits() ^ 0x5DEECE66DL, real.getLeastSignificantBits() ^ 0x1B873593L);
        GameProfile profile = new GameProfile(fakeId, recorded.name(), recorded.properties());
        // AbstractClientPlayer looks its skin up by UUID in the connection's player-info map.
        ((ClientPacketListenerAccessor) mc.getConnection()).minimals$playerInfoMap()
                .put(fakeId, new PlayerInfo(profile, false));
        RemotePlayer p = new RemotePlayer(level, profile);
        p.setId(FAKE_ID);
        Vec3 at = latest == null ? Vec3.ZERO : new Vec3(latest.x(), latest.y(), latest.z());
        p.setPos(at);
        p.setOldPosAndRot();
        level.addEntity(p);
        entity = p;
        wasSwinging = false;
        step = 0.0F;
        applyState(p);
    }

    private static void apply(RemotePlayer p, ReplayLocalTrack.Sample s) {
        double dx = s.x() - p.getX();
        double dy = s.y() - p.getY();
        double dz = s.z() - p.getZ();
        step = (float) Math.sqrt(dx * dx + dz * dz);
        p.setOldPosAndRot();
        p.setPos(s.x(), s.y(), s.z());
        p.setYRot(s.yRot());
        p.setXRot(s.xRot());
        p.setYHeadRot(s.headRot());
        p.setDeltaMovement(new Vec3(dx, dy, dz));
        p.setShiftKeyDown(s.has(ReplayLocalTrack.SNEAK));
        p.setSprinting(s.has(ReplayLocalTrack.SPRINT));
        p.setSwimming(s.has(ReplayLocalTrack.SWIM));
        // RemotePlayer does not derive its pose (updatePlayerPose is empty), so set it directly.
        p.setPose(s.has(ReplayLocalTrack.SWIM) ? Pose.SWIMMING
                : s.has(ReplayLocalTrack.SNEAK) ? Pose.CROUCHING : Pose.STANDING);
        boolean swing = s.has(ReplayLocalTrack.SWING);
        if (swing && !wasSwinging) {
            p.swing(InteractionHand.MAIN_HAND);
        }
        wasSwinging = swing;
    }
}
