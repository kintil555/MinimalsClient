package com.minimals.client.worldhost;

import com.minimals.client.worldhost.protocol.WHProtocolClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.local.LocalChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.network.ServerConnectionListener;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs on the world's host. When a friend's client asks the World Host relay to join,
 * the relay opens a "proxy connection" (identified by a connectionId) and streams raw
 * Minecraft protocol bytes through {@code WHC2SMessage.ProxyC2SPacket} /
 * {@code WHS2CMessage.ProxyC2SPacket}. This class bridges that stream to the local
 * integrated server using {@link ServerConnectionListener#startMemoryChannel()} (a
 * public vanilla API in 26.2), so no mixin is needed to reach the server's Netty pipeline.
 */
public final class ProxyHostBridge {

    private WHProtocolClient client;
    private SocketAddress memoryChannelAddress;
    private final Map<Long, RelayedClient> clients = new ConcurrentHashMap<>();

    public ProxyHostBridge(WHProtocolClient client) {
        this.client = client;
    }

    public void rebind(WHProtocolClient client) {
        this.client = client;
    }

    public void tick() {
        // Nothing periodic needed; connections are driven by incoming messages.
    }

    private SocketAddress memoryChannel() {
        if (memoryChannelAddress == null) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) return null;
            memoryChannelAddress = server.getConnection().startMemoryChannel();
        }
        return memoryChannelAddress;
    }

    public void onProxyConnect(long connectionId, InetAddress remoteAddr) {
        SocketAddress target = memoryChannel();
        if (target == null) {
            WorldHostManager.LOGGER.warn("Rejecting proxy join {}: no singleplayer server running", connectionId);
            if (client != null) client.proxyDisconnect(connectionId);
            return;
        }
        try {
            RelayedClient relayed = new RelayedClient(connectionId);
            clients.put(connectionId, relayed);
            relayed.start(target);
        } catch (Exception e) {
            WorldHostManager.LOGGER.error("Failed to start proxy relay for {}", connectionId, e);
            clients.remove(connectionId);
            if (client != null) client.proxyDisconnect(connectionId);
        }
    }

    public void onProxyData(long connectionId, byte[] data) {
        RelayedClient relayed = clients.get(connectionId);
        if (relayed != null) {
            relayed.sendToServer(data);
        }
    }

    public void onProxyDisconnect(long connectionId) {
        RelayedClient relayed = clients.remove(connectionId);
        if (relayed != null) {
            relayed.closeLocal();
        }
    }

    public void close() {
        clients.values().forEach(RelayedClient::closeLocal);
        clients.clear();
        memoryChannelAddress = null;
    }

    /** One relayed connection: a local Netty channel into the integrated server's memory listener. */
    private final class RelayedClient extends SimpleChannelInboundHandler<ByteBuf> {
        private static final int MAX_PACKET = 0xFFFF;
        private static final int MAX_PRE_ACTIVE_BYTES = 1 << 20;

        private final long connectionId;
        private ByteArrayOutputStream preActiveBuffer = new ByteArrayOutputStream();
        private volatile Channel channel;
        private volatile boolean closed;

        RelayedClient(long connectionId) {
            this.connectionId = connectionId;
        }

        void start(SocketAddress target) {
            new Bootstrap()
                    .group(EventLoopGroupHolder.local().eventLoopGroup())
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast("relay", RelayedClient.this);
                        }
                    })
                    .channel(LocalChannel.class)
                    .connect(target)
                    .syncUninterruptibly();
        }

        @Override
        public synchronized void channelActive(ChannelHandlerContext ctx) {
            channel = ctx.channel();
            if (preActiveBuffer.size() > 0) {
                doSend(preActiveBuffer.toByteArray());
            }
            preActiveBuffer = null;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            closed = true;
            clients.remove(connectionId);
            if (client != null) client.proxyDisconnect(connectionId);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            WorldHostManager.LOGGER.warn("Proxy relay {} error", connectionId, cause);
            ctx.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            // Data coming FROM the local integrated server, TO be sent to the joining friend.
            while (msg.readableBytes() > 0 && client != null) {
                int len = Math.min(msg.readableBytes(), MAX_PACKET);
                byte[] chunk = new byte[len];
                msg.readBytes(chunk);
                client.proxyPacket(connectionId, chunk);
            }
        }

        synchronized void sendToServer(byte[] data) {
            if (closed) return;
            if (channel == null) {
                // Pre-connect data is tiny in practice (login handshake). Bound it so a peer that
                // keeps sending while the local channel never activates can't grow it forever.
                if (preActiveBuffer == null || preActiveBuffer.size() + data.length > MAX_PRE_ACTIVE_BYTES) {
                    closeLocal();
                    return;
                }
                preActiveBuffer.writeBytes(data);
                return;
            }
            if (channel.eventLoop().inEventLoop()) {
                doSend(data);
            } else {
                channel.eventLoop().execute(() -> doSend(data));
            }
        }

        private void doSend(byte[] data) {
            channel.writeAndFlush(Unpooled.wrappedBuffer(data))
                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        synchronized void closeLocal() {
            if (closed) return;
            closed = true;
            if (channel != null) {
                channel.close();
            }
        }
    }
}
