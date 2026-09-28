package com.minimals.client.worldhost.protocol;

import com.minimals.client.worldhost.WorldHostManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.util.Crypt;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.security.PublicKey;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * TCP client for a World Host relay server (protocol version 7, matching
 * io.github.gaming32.worldhost / world-host-server-rust). Handles the encrypted
 * handshake (Mojang session auth, same mechanism used for online-mode servers) and
 * the send/receive message loop. UI-facing logic lives in {@link WorldHostManager}.
 */
public final class WHProtocolClient implements AutoCloseable {

    public static final int PROTOCOL_VERSION = 7;
    private static final int KEY_PREFIX = 0xFAFA0000;
    public static final int DEFAULT_PORT = 9646;
    private static final int MAX_MESSAGE_BYTES = 1 << 20; // 1 MiB
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int HANDSHAKE_TIMEOUT_MS = 15_000;
    private static final int SEND_QUEUE_LIMIT = 4096;

    private final String host;
    private final int port;
    private final Consumer<WHS2CMessage> onMessage;
    private final Runnable onClose;

    private final BlockingQueue<Optional<WHC2SMessage>> sendQueue = new LinkedBlockingQueue<>(SEND_QUEUE_LIMIT);
    private final CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
    private final CompletableFuture<Void> shutdownFuture = new CompletableFuture<>();

    private volatile boolean closed;
    private long connectionId;
    private volatile String baseIp = "";
    private volatile int basePort;

    public WHProtocolClient(String host, int port, User user, Consumer<WHS2CMessage> onMessage, Runnable onClose) {
        this.host = host;
        this.port = port;
        this.onMessage = onMessage;
        this.onClose = onClose;
        // Matches World Host's MAX_CONNECTION_IDS (1L << 42), so the id fits its 9-char base-36 encoding.
        this.connectionId = new java.security.SecureRandom().nextLong() & ((1L << 42) - 1);
        Thread.ofVirtual().name("MinimalsWH-connect").start(() -> runConnection(user));
    }

    private void runConnection(User user) {
        Socket socket = null;
        Cipher decryptCipher;
        Cipher encryptCipher;
        try {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            // Bound the handshake reads; cleared afterwards because the relay is idle for long stretches.
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            SecretKey secretKey = performHandshake(socket, user, connectionId);
            socket.setSoTimeout(0);
            socket.setKeepAlive(true);
            decryptCipher = Crypt.getCipher(Cipher.DECRYPT_MODE, secretKey);
            encryptCipher = Crypt.getCipher(Cipher.ENCRYPT_MODE, secretKey);
        } catch (Exception e) {
            WorldHostManager.LOGGER.error("Failed to connect to World Host server {}:{}", host, port, e);
            closeSocketQuietly(socket);
            close();
            return;
        }

        Socket fSocket = socket;
        Thread sendThread = Thread.ofVirtual().name("MinimalsWH-send").start(() -> sendLoop(fSocket, encryptCipher));
        Thread.ofVirtual().name("MinimalsWH-recv").start(() -> recvLoop(fSocket, decryptCipher));

        try {
            sendThread.join();
        } catch (InterruptedException ignored) {
        }
        closeSocketQuietly(fSocket);
        shutdownFuture.complete(null);
        onClose.run();
    }

    private void sendLoop(Socket socket, Cipher encryptCipher) {
        try {
            DataOutputStream dos = new DataOutputStream(new CipherOutputStream(socket.getOutputStream(), encryptCipher));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream tempDos = new DataOutputStream(baos);
            while (!closed) {
                Optional<WHC2SMessage> optionalMessage = sendQueue.take();
                if (optionalMessage.isEmpty()) break;
                WHC2SMessage message = optionalMessage.get();
                message.encode(tempDos);
                dos.writeInt(baos.size() + 1);
                dos.writeByte(message.typeId() & 0xff);
                dos.write(baos.toByteArray());
                baos.reset();
                dos.flush();
            }
        } catch (Exception e) {
            if (!closed) WorldHostManager.LOGGER.warn("World Host send thread stopped", e);
        }
        close();
    }

    private void recvLoop(Socket socket, Cipher decryptCipher) {
        try {
            DataInputStream dis = new DataInputStream(new CipherInputStream(socket.getInputStream(), decryptCipher));
            while (!closed) {
                int length = dis.readInt() - 1;
                if (length < 0) continue;
                if (length > MAX_MESSAGE_BYTES) {
                    // A relay never legitimately sends this much; a corrupt/hostile length would
                    // otherwise allocate up to 2 GB and crash the game with OutOfMemoryError.
                    throw new java.io.IOException("World Host message too large: " + length);
                }
                int typeId = dis.readUnsignedByte();
                byte[] body = new byte[length];
                dis.readFully(body);
                WHS2CMessage message;
                try {
                    message = WHS2CMessage.decode(typeId, new DataInputStream(new java.io.ByteArrayInputStream(body)));
                } catch (EOFException e) {
                    WorldHostManager.LOGGER.warn("World Host message {} decoded short", typeId);
                    continue;
                }
                if (message == null) continue;
                if (message instanceof WHS2CMessage.ConnectionInfo info) {
                    this.connectionId = info.connectionId();
                    this.baseIp = info.baseIp();
                    this.basePort = info.basePort();
                    connectedFuture.complete(null);
                }
                onMessage.accept(message);
            }
        } catch (Exception e) {
            if (!closed) {
                if (e instanceof SocketException) {
                    WorldHostManager.LOGGER.warn("World Host relay connection reset: {}", e.getMessage());
                } else {
                    WorldHostManager.LOGGER.warn("World Host recv thread stopped", e);
                }
            }
        }
        close();
    }

    private static SecretKey performHandshake(Socket socket, User user, long connectionId) throws Exception {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeInt(PROTOCOL_VERSION);
        dos.flush();

        DataInputStream dis = new DataInputStream(socket.getInputStream());
        if (dis.readInt() != KEY_PREFIX) {
            throw new IllegalStateException("World Host server does not support this client's auth protocol.");
        }

        byte[] publicKeyBytes = new byte[dis.readUnsignedShort()];
        dis.readFully(publicKeyBytes);
        byte[] challenge = new byte[dis.readUnsignedShort()];
        dis.readFully(challenge);

        SecretKey secretKey = Crypt.generateSecretKey();
        PublicKey publicKey = Crypt.byteToPublicKey(publicKeyBytes);
        String authKey = new BigInteger(Crypt.digestData("", publicKey, secretKey)).toString(16);

        byte[] encryptedChallenge = Crypt.encryptUsingKey(publicKey, challenge);
        dos.writeShort(encryptedChallenge.length);
        dos.write(encryptedChallenge);
        dos.flush();

        byte[] encryptedSecretKey = Crypt.encryptUsingKey(publicKey, secretKey.getEncoded());
        dos.writeShort(encryptedSecretKey.length);
        dos.write(encryptedSecretKey);
        dos.flush();

        UUID profileId = user.getProfileId();
        if (profileId.version() == 4) {
            // Real (non-offline) Mojang account: authenticate this connection with the
            // session service, exactly like joining an online-mode server.
            Minecraft.getInstance().services().sessionService()
                .joinServer(profileId, user.getAccessToken(), authKey);
        }

        WHC2SMessage.writeUuid(dos, profileId);
        WHC2SMessage.writeString(dos, user.getName());
        dos.writeLong(connectionId);
        dos.flush();

        return secretKey;
    }

    private void enqueue(WHC2SMessage message) {
        if (closed) return;
        // offer(), not put(): enqueue is called from the game thread, and a stalled socket must
        // drop messages / tear down instead of freezing the whole client on a full queue.
        if (!sendQueue.offer(Optional.of(message))) {
            WorldHostManager.LOGGER.warn("World Host send queue full, closing connection");
            close();
        }
    }

    public void listOnline(Collection<UUID> friends) {
        enqueue(new WHC2SMessage.ListOnline(friends));
    }

    public void friendRequest(UUID toUser) {
        enqueue(new WHC2SMessage.FriendRequest(toUser));
    }

    public void publishedWorld(Collection<UUID> friends) {
        enqueue(new WHC2SMessage.PublishedWorld(friends));
    }

    public void closedWorld(Collection<UUID> friends) {
        enqueue(new WHC2SMessage.ClosedWorld(friends));
    }

    public void joinGranted(long connectionId) {
        enqueue(new WHC2SMessage.JoinGranted(connectionId, true));
    }

    /** Sent by a joiner to ask the relay to open a proxy connection to connectionId's world. */
    public void requestDirectJoin(long connectionId) {
        enqueue(new WHC2SMessage.RequestDirectJoin(connectionId));
    }

    public void proxyPacket(long connectionId, byte[] data) {
        enqueue(new WHC2SMessage.ProxyC2SPacket(connectionId, data));
    }

    public void proxyDisconnect(long connectionId) {
        enqueue(new WHC2SMessage.ProxyDisconnect(connectionId));
    }

    public String getBaseIp() {
        return baseIp;
    }

    public int getBasePort() {
        return basePort;
    }

    public long getConnectionId() {
        return connectionId;
    }

    public boolean isClosed() {
        return closed;
    }

    public CompletableFuture<Void> getConnectedFuture() {
        return connectedFuture;
    }

    private static void closeSocketQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        sendQueue.clear();
        sendQueue.offer(Optional.empty());
    }
}
