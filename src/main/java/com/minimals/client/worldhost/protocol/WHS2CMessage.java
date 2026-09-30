package com.minimals.client.worldhost.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Messages received from the World Host relay server. Mirrors the subset of
 * io.github.gaming32.worldhost's WorldHostS2CMessage protocol (protocol version 7)
 * that MinimalsClient's friend-join feature uses.
 */
public sealed interface WHS2CMessage {

    record Error(String message, boolean critical) implements WHS2CMessage {
        public static final int ID = 0;
        public static Error decode(DataInputStream dis) throws IOException {
            return new Error(readString(dis), dis.read() > 0);
        }
    }

    record IsOnlineTo(UUID user) implements WHS2CMessage {
        public static final int ID = 1;
        public static IsOnlineTo decode(DataInputStream dis) throws IOException {
            return new IsOnlineTo(readUuid(dis));
        }
    }

    record OnlineGame(String host, int port, long ownerCid) implements WHS2CMessage {
        public static final int ID = 2;
        public static OnlineGame decode(DataInputStream dis) throws IOException {
            String host = readString(dis);
            int port = dis.readUnsignedShort();
            long ownerCid = dis.readLong();
            dis.readBoolean(); // isPunchProtocol, unused (no hole punching support; proxy only)
            return new OnlineGame(host, port, ownerCid);
        }
    }

    record FriendRequest(UUID fromUser, int security) implements WHS2CMessage {
        public static final int ID = 3;
        public static FriendRequest decode(DataInputStream dis) throws IOException {
            return new FriendRequest(readUuid(dis), dis.readUnsignedByte());
        }
    }

    record PublishedWorld(UUID user, long connectionId, int security) implements WHS2CMessage {
        public static final int ID = 4;
        public static PublishedWorld decode(DataInputStream dis) throws IOException {
            return new PublishedWorld(readUuid(dis), dis.readLong(), dis.readUnsignedByte());
        }
    }

    record ClosedWorld(UUID user) implements WHS2CMessage {
        public static final int ID = 5;
        public static ClosedWorld decode(DataInputStream dis) throws IOException {
            return new ClosedWorld(readUuid(dis));
        }
    }

    record RequestJoin(UUID user, long connectionId, int security) implements WHS2CMessage {
        public static final int ID = 6;
        public static RequestJoin decode(DataInputStream dis) throws IOException {
            return new RequestJoin(readUuid(dis), dis.readLong(), dis.readUnsignedByte());
        }
    }

    record ProxyC2SPacket(long connectionId, byte[] data) implements WHS2CMessage {
        public static final int ID = 9;
        public static ProxyC2SPacket decode(DataInputStream dis) throws IOException {
            return new ProxyC2SPacket(dis.readLong(), dis.readAllBytes());
        }
    }

    record ProxyConnect(long connectionId, InetAddress remoteAddr) implements WHS2CMessage {
        public static final int ID = 10;
        public static ProxyConnect decode(DataInputStream dis) throws IOException {
            return new ProxyConnect(dis.readLong(), InetAddress.getByAddress(dis.readNBytes(dis.readUnsignedByte())));
        }
    }

    record ProxyDisconnect(long connectionId) implements WHS2CMessage {
        public static final int ID = 11;
        public static ProxyDisconnect decode(DataInputStream dis) throws IOException {
            return new ProxyDisconnect(dis.readLong());
        }
    }

    record ConnectionInfo(
        long connectionId, String baseIp, int basePort, String userIp, int protocolVersion
    ) implements WHS2CMessage {
        public static final int ID = 12;
        public static ConnectionInfo decode(DataInputStream dis) throws IOException {
            long connectionId = dis.readLong();
            String baseIp = readString(dis);
            int basePort = dis.readUnsignedShort();
            String userIp = readString(dis);
            int protocolVersion = dis.readInt();
            dis.readUnsignedShort(); // punchPort, unused (no hole punching support)
            return new ConnectionInfo(connectionId, baseIp, basePort, userIp, protocolVersion);
        }
    }

    record ConnectionNotFound(long connectionId) implements WHS2CMessage {
        public static final int ID = 15;
        public static ConnectionNotFound decode(DataInputStream dis) throws IOException {
            return new ConnectionNotFound(dis.readLong());
        }
    }

    record Warning(String message, boolean important) implements WHS2CMessage {
        public static final int ID = 17;
        public static Warning decode(DataInputStream dis) throws IOException {
            return new Warning(readString(dis), dis.readBoolean());
        }
    }

    /** A friend asks us (the host) for our world's status. Answer with NewQueryResponse. */
    record QueryRequest(UUID friend, long connectionId, int security) implements WHS2CMessage {
        public static final int ID = 7;
        public static QueryRequest decode(DataInputStream dis) throws IOException {
            return new QueryRequest(readUuid(dis), dis.readLong(), dis.readUnsignedByte());
        }
    }

    /** A friend's world answered our query. {@code status} is the raw serialized ServerStatus. */
    record NewQueryResponse(UUID friend, byte[] status) implements WHS2CMessage {
        public static final int ID = 16;
        public static NewQueryResponse decode(DataInputStream dis) throws IOException {
            return new NewQueryResponse(readUuid(dis), dis.readAllBytes());
        }
    }

    static WHS2CMessage decode(int id, DataInputStream dis) throws IOException {
        return switch (id) {
            case Error.ID -> Error.decode(dis);
            case IsOnlineTo.ID -> IsOnlineTo.decode(dis);
            case OnlineGame.ID -> OnlineGame.decode(dis);
            case FriendRequest.ID -> FriendRequest.decode(dis);
            case PublishedWorld.ID -> PublishedWorld.decode(dis);
            case ClosedWorld.ID -> ClosedWorld.decode(dis);
            case RequestJoin.ID -> RequestJoin.decode(dis);
            case ProxyC2SPacket.ID -> ProxyC2SPacket.decode(dis);
            case ProxyConnect.ID -> ProxyConnect.decode(dis);
            case ProxyDisconnect.ID -> ProxyDisconnect.decode(dis);
            case ConnectionInfo.ID -> ConnectionInfo.decode(dis);
            case ConnectionNotFound.ID -> ConnectionNotFound.decode(dis);
            case Warning.ID -> Warning.decode(dis);
            case QueryRequest.ID -> QueryRequest.decode(dis);
            case NewQueryResponse.ID -> NewQueryResponse.decode(dis);
            default -> null; // unknown/unused message type: skip
        };
    }

    static UUID readUuid(DataInputStream dis) throws IOException {
        return new UUID(dis.readLong(), dis.readLong());
    }

    static String readString(DataInputStream dis) throws IOException {
        byte[] buf = new byte[dis.readUnsignedShort()];
        dis.readFully(buf);
        return new String(buf, StandardCharsets.UTF_8);
    }
}
