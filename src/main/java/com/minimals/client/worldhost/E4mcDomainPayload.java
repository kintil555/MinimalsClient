package com.minimals.client.worldhost;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent by the host, over the normal game connection, right after a friend finishes
 * joining through the World Host relay proxy. Carries the host's e4mc-assigned domain
 * so the joiner can silently reconnect over real P2P (iroh/QUIC) instead of staying on
 * the relay. We do NOT put this in World Host's own protocol (WHC2SMessage/WHS2CMessage)
 * because that's the relay server's real wire format — adding a field there would break
 * compatibility with the actual world-host relay. This rides Minecraft's own custom
 * payload channel instead, which only host and joiner (both running MinimalsClient) see.
 */
public record E4mcDomainPayload(String domain) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<E4mcDomainPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("minimals", "e4mc_domain"));

    public static final StreamCodec<RegistryFriendlyByteBuf, E4mcDomainPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.domain(), 256),
            buf -> new E4mcDomainPayload(buf.readUtf(256))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
