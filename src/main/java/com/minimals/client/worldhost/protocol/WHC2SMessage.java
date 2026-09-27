package com.minimals.client.worldhost.protocol;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

/**
 * Messages this client sends to the World Host relay server. Mirrors the subset of
 * io.github.gaming32.worldhost's WorldHostC2SMessage protocol (protocol version 7)
 * that MinimalsClient's friend-join feature actually uses: presence/friend list,
 * publish/close world, join request handling, and the TCP proxy relay.
 */
public sealed interface WHC2SMessage {

    byte typeId();

    void encode(DataOutputStream dos) throws IOException;

    record ListOnline(Collection<UUID> friends) implements WHC2SMessage {
        public byte typeId() { return 0; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeInt(friends.size());
            for (UUID friend : friends) writeUuid(dos, friend);
        }
    }

    record FriendRequest(UUID toUser) implements WHC2SMessage {
        public byte typeId() { return 1; }
        public void encode(DataOutputStream dos) throws IOException {
            writeUuid(dos, toUser);
        }
    }

    record PublishedWorld(Collection<UUID> friends) implements WHC2SMessage {
        public byte typeId() { return 2; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeInt(friends.size());
            for (UUID friend : friends) writeUuid(dos, friend);
        }
    }

    record ClosedWorld(Collection<UUID> friends) implements WHC2SMessage {
        public byte typeId() { return 3; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeInt(friends.size());
            for (UUID friend : friends) writeUuid(dos, friend);
        }
    }

    record JoinGranted(long connectionId, boolean proxyJoin) implements WHC2SMessage {
        public byte typeId() { return 5; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeLong(connectionId);
            // JoinType: only Proxy (1) is supported by this client (no UPnP/hole punching).
            dos.writeByte(1);
        }
    }

    record QueryRequest(Collection<UUID> friends) implements WHC2SMessage {
        public byte typeId() { return 6; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeInt(friends.size());
            for (UUID friend : friends) writeUuid(dos, friend);
        }
    }

    record ProxyC2SPacket(long connectionId, byte[] data) implements WHC2SMessage {
        public byte typeId() { return 8; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeLong(connectionId);
            dos.write(data);
        }
    }

    record ProxyDisconnect(long connectionId) implements WHC2SMessage {
        public byte typeId() { return 9; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeLong(connectionId);
        }
    }

    record RequestDirectJoin(long connectionId) implements WHC2SMessage {
        public byte typeId() { return 10; }
        public void encode(DataOutputStream dos) throws IOException {
            dos.writeLong(connectionId);
        }
    }

    static void writeUuid(DataOutputStream dos, UUID uuid) throws IOException {
        dos.writeLong(uuid.getMostSignificantBits());
        dos.writeLong(uuid.getLeastSignificantBits());
    }

    static void writeString(DataOutputStream dos, String string) throws IOException {
        byte[] buf = string.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(buf.length);
        dos.write(buf);
    }
}
