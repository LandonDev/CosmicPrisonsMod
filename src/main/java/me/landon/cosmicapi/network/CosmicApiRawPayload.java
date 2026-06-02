package me.landon.cosmicapi.network;

import java.util.Arrays;
import java.util.Objects;
import me.landon.cosmicapi.protocol.CosmicApiProtocolConstants;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

public final class CosmicApiRawPayload implements CustomPayload {
    public static final CustomPayload.Id<CosmicApiRawPayload> ID =
            new CustomPayload.Id<>(CosmicApiProtocolConstants.CHANNEL_ID);
    public static final PacketCodec<PacketByteBuf, CosmicApiRawPayload> CODEC =
            PacketCodec.of(CosmicApiRawPayload::encode, CosmicApiRawPayload::decode);

    private final byte[] payloadBytes;

    public CosmicApiRawPayload(byte[] payloadBytes) {
        this.payloadBytes = Objects.requireNonNull(payloadBytes, "payloadBytes").clone();
        if (this.payloadBytes.length > CosmicApiProtocolConstants.MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Cosmic API payload exceeds maximum allowed bytes");
        }
    }

    private static CosmicApiRawPayload decode(PacketByteBuf buf) {
        return new CosmicApiRawPayload(
                buf.readByteArray(CosmicApiProtocolConstants.MAX_PACKET_BYTES));
    }

    private static void encode(CosmicApiRawPayload payload, PacketByteBuf buf) {
        buf.writeByteArray(payload.payloadBytes);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public byte[] payloadBytes() {
        return Arrays.copyOf(payloadBytes, payloadBytes.length);
    }
}
