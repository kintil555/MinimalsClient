package com.minimals.client.replay;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Pose;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Encoding of the recording player's own state, stored as PROTO_LOCAL frames.
 *
 * <pre>
 * profile : byte 0, GameProfile (vanilla codec, textures included)
 * sample  : byte 1, 3 x double xyz, 3 x float yRot xRot headRot, byte flags
 * </pre>
 */
public final class ReplayLocalTrack {

    public static final byte KIND_PROFILE = 0;
    public static final byte KIND_SAMPLE = 1;

    public static final int SNEAK = 1;
    public static final int SPRINT = 2;
    public static final int SWIM = 4;
    public static final int SWING = 8;

    public record Sample(double x, double y, double z, float yRot, float xRot, float headRot, int flags) {
        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    private ReplayLocalTrack() {
    }

    public static byte[] encodeProfile(LocalPlayer player, GameProfile account, RegistryAccess access) {
        GameProfile own = player.getGameProfile();
        // The account profile carries the skin textures; the in-world one may not (singleplayer).
        GameProfile profile = account != null && account.id().equals(own.id()) ? account : own;
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        buf.writeByte(KIND_PROFILE);
        ByteBufCodecs.GAME_PROFILE.encode(buf, profile);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    public static GameProfile decodeProfile(byte[] data, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), access);
        try {
            buf.readByte();
            return ByteBufCodecs.GAME_PROFILE.decode(buf);
        } finally {
            buf.release();
        }
    }

    public static byte[] encodeSample(LocalPlayer p) {
        int flags = 0;
        if (p.isShiftKeyDown() || p.getPose() == Pose.CROUCHING) {
            flags |= SNEAK;
        }
        if (p.isSprinting()) {
            flags |= SPRINT;
        }
        if (p.isSwimming()) {
            flags |= SWIM;
        }
        if (p.swinging) {
            flags |= SWING;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(48);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(KIND_SAMPLE);
            out.writeDouble(p.getX());
            out.writeDouble(p.getY());
            out.writeDouble(p.getZ());
            out.writeFloat(p.getYRot());
            out.writeFloat(p.getXRot());
            out.writeFloat(p.getYHeadRot());
            out.writeByte(flags);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bytes.toByteArray();
    }

    public static Sample decodeSample(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte();
            return new Sample(in.readDouble(), in.readDouble(), in.readDouble(),
                    in.readFloat(), in.readFloat(), in.readFloat(), in.readUnsignedByte());
        } catch (IOException e) {
            return null;
        }
    }
}
