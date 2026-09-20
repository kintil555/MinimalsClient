package com.minimals.client.replay;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

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
 * state   : byte 2, byte skinLayers (player model customisation mask), byte slotMask,
 *           then one ItemStack (vanilla OPTIONAL_STREAM_CODEC) per bit set in slotMask
 * </pre>
 * The state frame is only written when something changed, like Flashback does for the local
 * player's entity data and equipment.
 */
public final class ReplayLocalTrack {

    public static final byte KIND_PROFILE = 0;
    public static final byte KIND_SAMPLE = 1;
    public static final byte KIND_STATE = 2;

    /** Slots tracked by the state frame, bit index = position in this array. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    public static final int SNEAK = 1;
    public static final int SPRINT = 2;
    public static final int SWIM = 4;
    public static final int SWING = 8;

    public record Sample(double x, double y, double z, float yRot, float xRot, float headRot, int flags) {
        public boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    /** Skin layers + the equipment slots that were present in a state frame. */
    public record State(int skinLayers, int slotMask, ItemStack[] items) {
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

    /** Current skin layer mask of the player (jacket, sleeves, pants, hat, cape). */
    public static int skinLayers(LocalPlayer p) {
        int mask = 0;
        for (net.minecraft.world.entity.player.PlayerModelPart part : net.minecraft.world.entity.player.PlayerModelPart.values()) {
            if (p.isModelPartShown(part)) {
                mask |= part.getMask();
            }
        }
        return mask;
    }

    public static ItemStack[] readEquipment(LocalPlayer p) {
        ItemStack[] items = new ItemStack[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            items[i] = p.getItemBySlot(SLOTS[i]).copy();
        }
        return items;
    }

    public static boolean sameEquipment(ItemStack[] a, ItemStack[] b) {
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (!ItemStack.matches(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    public static byte[] encodeState(int skinLayers, ItemStack[] items, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        buf.writeByte(KIND_STATE);
        buf.writeByte(skinLayers);
        int mask = 0;
        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty()) {
                mask |= 1 << i;
            }
        }
        buf.writeByte(mask);
        for (int i = 0; i < items.length; i++) {
            if ((mask & (1 << i)) != 0) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, items[i]);
            }
        }
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    public static State decodeState(byte[] data, RegistryAccess access) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), access);
        try {
            buf.readByte();
            int layers = buf.readUnsignedByte();
            int mask = buf.readUnsignedByte();
            ItemStack[] items = new ItemStack[SLOTS.length];
            for (int i = 0; i < items.length; i++) {
                items[i] = (mask & (1 << i)) != 0 ? ItemStack.OPTIONAL_STREAM_CODEC.decode(buf) : ItemStack.EMPTY;
            }
            return new State(layers, mask, items);
        } catch (RuntimeException e) {
            return null;
        } finally {
            buf.release();
        }
    }
}
