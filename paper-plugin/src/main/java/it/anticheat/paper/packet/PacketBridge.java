package it.anticheat.paper.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedEnumEntityUseAction;
import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import it.anticheat.core.PlayerData;
import it.anticheat.paper.AnticheatPaper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.UUID;

/**
 * Hook ProtocolLib (OPZIONALE, solo Paper).
 * - Timer preciso: conta i Flying reali (legit 20/s), non gli eventi.
 * - Range preciso: distanza occhio -> hitbox su ogni attacco (killer
 *   degli Hitboxes/Reach), poi riusa la logica Reach/KillAura del core.
 * Ogni listener e isolato in try/catch: se la versione di ProtocolLib o di
 * Minecraft non espone un pacchetto, quel singolo check resta spento e gli
 * altri (piu quelli a eventi) continuano. Tecniche ispirate a Grim,
 * implementazione originale.
 */
public class PacketBridge {
    private final AnticheatPaper plugin;

    public PacketBridge(AnticheatPaper plugin) {
        this.plugin = plugin;
        ProtocolManager protocol;
        try {
            protocol = ProtocolLibrary.getProtocolManager();
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] ProtocolLib non utilizzabile: " + t.getMessage());
            return;
        }
        hookFlying(protocol);
        hookUseEntity(protocol);
    }

    private void hookFlying(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.FLYING,
                    PacketType.Play.Client.POSITION,
                    PacketType.Play.Client.POSITION_LOOK,
                    PacketType.Play.Client.LOOK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        AnticheatCore.get().handleFlyingPacket(event.getPlayer().getUniqueId());
                    } catch (Throwable ignored) {}
                }
            });
            plugin.getLogger().info("[AC] Packet: timer Flying attivo.");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] Packet timer non disponibile: " + t.getMessage());
        }
    }

    private void hookUseEntity(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.USE_ENTITY) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    int entityId;
                    try {
                        entityId = event.getPacket().getIntegers().read(0);
                        // Tipo azione via wrapper+reflection: evita di nominare
                        // il tipo annidato (non referenziabile in alcune build).
                        Object raw = event.getPacket().getEnumEntityUseActions().read(0);
                        Object wrapped = WrappedEnumEntityUseAction.fromHandle(raw);
                        Object action = wrapped.getClass().getMethod("getAction").invoke(wrapped);
                        // solo ATTACK: interact (ceste, trade, armature) non interessa
                        if (!"ATTACK".equals(String.valueOf(action))) return;
                    } catch (Throwable t) {
                        return;
                    }
                    UUID uuid = event.getPlayer().getUniqueId();
                    // Bukkit API solo dal main thread
                    Bukkit.getScheduler().runTask(plugin, () -> evaluateAttackRange(uuid, entityId));
                }
            });
            AnticheatCore.get().setPacketFightPrimary(true);
            plugin.getLogger().info("[AC] Packet: range attacchi preciso attivo (eventi fight disattivati).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] Packet range non disponibile: " + t.getMessage());
        }
    }

    /** Misura occhio -> hitbox e riusa Reach/KillAura del core. Main thread. */
    private void evaluateAttackRange(UUID uuid, int entityId) {
        Player p;
        try {
            p = Bukkit.getPlayer(uuid);
            if (p == null) return;
        } catch (Throwable t) {
            return;
        }
        Entity target = null;
        try {
            for (Entity e : p.getWorld().getEntities()) {
                if (e.getEntityId() == entityId) {
                    target = e;
                    break;
                }
            }
        } catch (Throwable t) {
            return;
        }
        if (target == null || target.equals(p)) return;

        Location eye;
        Location feet;
        try {
            eye = p.getEyeLocation();
            feet = p.getLocation();
        } catch (Throwable t) {
            return;
        }

        // distanza occhio -> punto piu vicino della hitbox (precisa, non stimata)
        double eyeDist;
        try {
            BoundingBox box = target.getBoundingBox();
            double cx = clamp(eye.getX(), box.getMinX(), box.getMaxX());
            double cy = clamp(eye.getY(), box.getMinY(), box.getMaxY());
            double cz = clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
            double dx = eye.getX() - cx;
            double dy = eye.getY() - cy;
            double dz = eye.getZ() - cz;
            eyeDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        } catch (Throwable t) {
            try {
                eyeDist = eye.distance(target.getLocation());
            } catch (Throwable t2) {
                return;
            }
        }

        Check.FightContext ctx = new Check.FightContext();
        ctx.distance = eyeDist; // misura precisa: usa distance, eyeDistance resta 0
        ctx.eyeDistance = 0;
        ctx.dtSinceLastAttackMillis = -1; // calcolato dal core
        try {
            ctx.ping = p.getPing();
        } catch (Throwable t) {
            ctx.ping = 0;
        }
        try {
            Location tl = target.getLocation();
            double dx = tl.getX() - feet.getX();
            double dz = tl.getZ() - feet.getZ();
            double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double diff = Math.abs(targetYaw - feet.getYaw()) % 360;
            if (diff > 180) diff = 360 - diff;
            ctx.targetYawDiff = diff;
            ctx.attackerYaw = feet.getYaw();
            ctx.attackerPitch = feet.getPitch();
        } catch (Throwable t) {
            ctx.targetYawDiff = 0;
        }
        try {
            ctx.blocking = p.isBlocking();
        } catch (Throwable t) {
            ctx.blocking = false;
        }
        int before = AnticheatCore.get().data(uuid).totalVl();
        AnticheatCore.get().handleFight(uuid, p.getName(), ctx);
        AnticheatCore.get().handleClick(uuid, p.getName());
        if (AnticheatCore.get().isDebug(uuid)) {
            PlayerData d = AnticheatCore.get().data(uuid);
            Bukkit.getLogger().info("[AC-DBG] pkt-fight " + p.getName()
                + " vs " + target.getType()
                + " eyeBox=" + String.format("%.2f", eyeDist)
                + " VL " + before + "->" + d.totalVl() + " " + d.violations);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
