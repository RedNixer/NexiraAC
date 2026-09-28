package it.anticheat.fabric;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload "anticheat:hello". Formato stringa k=v;k=v per restare compatibile
 * con il listener Paper (PluginMessage) senza NBT complessi.
 */
public record HelloPayload(String text) implements CustomPacketPayload {
    public static final Identifier ID_LOC = Identifier.fromNamespaceAndPath("anticheat", "hello");
    public static final Type<HelloPayload> TYPE = new Type<>(ID_LOC);
    public static final StreamCodec<ByteBuf, HelloPayload> CODEC =
        ByteBufCodecs.STRING_UTF8.map(HelloPayload::new, HelloPayload::text);

    public static HelloPayload make(String modVersion) {
        return new HelloPayload("mod=anticheat-client;ver=" + modVersion + ";proto=1");
    }

    public static String extractVersion(String text) {
        for (String part : text.split("[;\\n]")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase("ver")) return kv[1].trim();
        }
        return "?";
    }

    public static String encode(String s) {
        return new String(s.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
