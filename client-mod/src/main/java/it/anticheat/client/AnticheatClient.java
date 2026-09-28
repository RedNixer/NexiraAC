package it.anticheat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Mod client opzionale:
 * - al join invia "anticheat:hello" con la versione (badge verificato sul server)
 * - tasto F8 (cambiabile in Opzioni > Comandi) per aprire la GUI admin (solo staff)
 * Il server la usa solo come segnale, MAI per fidarsi del client.
 */
public class AnticheatClient implements ClientModInitializer {

    public record HelloPayload(String text) implements CustomPacketPayload {
        public static final Identifier ID_LOC = Identifier.fromNamespaceAndPath("anticheat", "hello");
        public static final Type<HelloPayload> TYPE = new Type<>(ID_LOC);
        public static final StreamCodec<ByteBuf, HelloPayload> CODEC =
            ByteBufCodecs.STRING_UTF8.map(HelloPayload::new, HelloPayload::text);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Payload vuoto: "apri GUI admin". Il server ricontrolla i permessi. */
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

    private static KeyMapping openGuiKey;
    private static boolean helloSent = false;

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenGuiPayload.TYPE, OpenGuiPayload.CODEC);

        KeyMapping.Category acCategory =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("anticheat", "admin"));
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.anticheat.opengui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, acCategory));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            helloSent = false; // verra inviato al primo tick utile (vedi sotto)
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> helloSent = false);

        // handshake dal server (Paper invia plugin message, Fabric payload): rispondi comunque
        ClientPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                    ClientPlayNetworking.send(new HelloPayload(
                        "mod=anticheat-client;ver=" + clientVersion() + ";proto=1"));
                }
            });
        });

        // F8 -> chiedi al server di aprire la GUI admin (il server verifica i permessi)
        // + invio hello ritardato: aspetta che il server registri il canale (sicuro anche su Paper)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!helloSent && client.player != null && ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
                ClientPlayNetworking.send(new HelloPayload(
                    "mod=anticheat-client;ver=" + clientVersion() + ";proto=1"));
                helloSent = true;
            }
            while (openGuiKey.consumeClick()) {
                if (client.player != null && ClientPlayNetworking.canSend(OpenGuiPayload.TYPE)) {
                    ClientPlayNetworking.send(new OpenGuiPayload());
                }
            }
        });
    }

    private static String clientVersion() {
        return FabricLoader.getInstance()
            .getModContainer("anticheat-client")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("0.1.0");
    }
}
