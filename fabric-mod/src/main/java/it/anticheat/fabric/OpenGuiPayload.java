package it.anticheat.fabric;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Payload vuoto "anticheat:opengui": il client chiede l'apertura del pannello admin. */
public record OpenGuiPayload() implements CustomPacketPayload {
    public static final Identifier ID_LOC = Identifier.fromNamespaceAndPath("anticheat", "opengui");
    public static final Type<OpenGuiPayload> TYPE = new Type<>(ID_LOC);
    public static final StreamCodec<ByteBuf, OpenGuiPayload> CODEC =
        StreamCodec.unit(new OpenGuiPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
