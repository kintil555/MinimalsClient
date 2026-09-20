package com.minimals.client.replay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * .mreplay file: a GZIP stream of records, one per clientbound packet frame.
 *
 * <pre>
 * header : int MAGIC, int VERSION, UTF name, UTF mcVersion, long startedAtMillis
 *          (VERSION 2 adds: byte hasPose, then 3 x double xyz + 2 x float yRot xRot when hasPose != 0)
 * record : byte protocol (0 login, 1 configuration, 2 play), int tick, int length, bytes
 * end    : byte -1, int totalTicks
 * </pre>
 *
 * Frames are the raw, already decrypted/decompressed packet bytes (packet id + payload), i.e. exactly
 * what the vanilla PacketDecoder receives. Playback pushes the same bytes back through the vanilla
 * decoder, so no packet is ever re-serialised and registry-dependent codecs stay correct.
 */
public final class ReplayFormat {

    public static final int MAGIC = 0x4D525031; // "MRP1"
    public static final int VERSION = 2;
    public static final byte PROTO_LOGIN = 0;
    public static final byte PROTO_CONFIG = 1;
    public static final byte PROTO_PLAY = 2;
    public static final byte END = -1;

    private ReplayFormat() {
    }

    public record Meta(String name, String mcVersion, long startedAt, int totalTicks) {
    }

    /** Where the recorded player stood (eye-independent feet position) when the clip started. */
    public record Pose(double x, double y, double z, float yRot, float xRot) {
    }

    public record Frame(byte protocol, int tick, byte[] data) {
    }

    public static final class Writer implements AutoCloseable {
        private final DataOutputStream out;
        private int lastTick;

        public Writer(Path file, String name, String mcVersion, long startedAt) throws IOException {
            this(file, name, mcVersion, startedAt, null);
        }

        public Writer(Path file, String name, String mcVersion, long startedAt, Pose pose) throws IOException {
            Files.createDirectories(file.getParent());
            this.out = new DataOutputStream(new GZIPOutputStream(
                    Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
                    1 << 16, true)); // syncFlush=true: flush() makes everything so far readable
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(name);
            out.writeUTF(mcVersion);
            out.writeLong(startedAt);
            out.writeByte(pose == null ? 0 : 1);
            if (pose != null) {
                out.writeDouble(pose.x());
                out.writeDouble(pose.y());
                out.writeDouble(pose.z());
                out.writeFloat(pose.yRot());
                out.writeFloat(pose.xRot());
            }
        }

        public void write(byte protocol, int tick, byte[] data) throws IOException {
            out.writeByte(protocol);
            out.writeInt(tick);
            out.writeInt(data.length);
            out.write(data);
            lastTick = Math.max(lastTick, tick);
        }

        /** Makes every frame written so far readable from disk (a reader may stop at EOF). */
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.writeByte(END);
            out.writeInt(lastTick);
            out.close();
        }
    }

    public static final class Reader implements AutoCloseable {
        private final DataInputStream in;
        private final Meta header;
        private final Pose pose;
        private int totalTicks = -1;

        public Reader(Path file) throws IOException {
            this.in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file), 1 << 16));
            if (in.readInt() != MAGIC) {
                throw new IOException("Not a .mreplay file");
            }
            int version = in.readInt();
            if (version < 1 || version > VERSION) {
                throw new IOException("Unsupported replay version " + version);
            }
            this.header = new Meta(in.readUTF(), in.readUTF(), in.readLong(), -1);
            Pose p = null;
            if (version >= 2 && in.readByte() != 0) {
                p = new Pose(in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat());
            }
            this.pose = p;
        }

        public Meta header() {
            return header;
        }

        /** Player pose at the moment the clip was started, or null for old / pose-less files. */
        public Pose pose() {
            return pose;
        }

        /** Next frame, or null at end of file. */
        public Frame next() throws IOException {
            byte protocol;
            try {
                protocol = in.readByte();
            } catch (java.io.EOFException | java.util.zip.ZipException eof) {
                return null; // unfinished buffer: everything flushed so far is valid
            }
            if (protocol == END) {
                totalTicks = in.readInt();
                return null;
            }
            try {
                int tick = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > (1 << 26)) {
                    throw new IOException("Corrupt frame length " + len);
                }
                byte[] data = new byte[len];
                in.readFully(data);
                return new Frame(protocol, tick, data);
            } catch (java.io.EOFException | java.util.zip.ZipException eof) {
                return null; // last frame was cut mid-write
            }
        }

        public int totalTicks() {
            return totalTicks;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    /** Reads only the header and scans to the end to learn the duration (used by the replay list). */
    public static Meta readMeta(Path file) throws IOException {
        try (Reader r = new Reader(file)) {
            Meta h = r.header();
            int last = 0;
            Frame f;
            while ((f = r.next()) != null) {
                last = f.tick();
            }
            return new Meta(h.name(), h.mcVersion(), h.startedAt(), r.totalTicks() >= 0 ? r.totalTicks() : last);
        }
    }
}
