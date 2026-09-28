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
        hookRotationStream(protocol);
        hookSwing(protocol);
        hookGroundSpoof(protocol);
    }

    private void hookFlying(ProtocolManager protocol) {
        try {
            // NOTA: niente PacketType.Play.Client.FLYING generico: su 1.21.11
            // non e registrato (warn "unknown packet" nei log). I 4 tipi
            // specifici coprono tutto: POSITION, POSITION_LOOK, LOOK, GROUND.
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.POSITION,
                    PacketType.Play.Client.POSITION_LOOK,
                    PacketType.Play.Client.GROUND,
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
                    boolean isAttack;
                    try {
                        entityId = event.getPacket().getIntegers().read(0);
                        // Tipo azione via wrapper+reflection: evita di nominare
                        // il tipo annidato (non referenziabile in alcune build).
                        Object raw = event.getPacket().getEnumEntityUseActions().read(0);
                        Object wrapped = WrappedEnumEntityUseAction.fromHandle(raw);
                        Object action = wrapped.getClass().getMethod("getAction").invoke(wrapped);
                        isAttack = "ATTACK".equals(String.valueOf(action));
                    } catch (Throwable t) {
                        return;
                    }
                    UUID uuid = event.getPlayer().getUniqueId();
                    String pname;
                    try {
                        pname = event.getPlayer().getName();
                    } catch (Throwable t) {
                        return;
                    }
                    if (!isAttack) {
                        // Passo 3 F2/C2: interact non-attacco (stand, frame, trade)
                        Bukkit.getScheduler().runTask(plugin,
                            () -> evaluateInteractRange(uuid, pname, entityId));
                        return;
                    }
                    // P2: ordine pacchetti PRIMA della misura range (thread pacchetto, puro core)
                    try {
                        AnticheatCore.get().handlePacketAttack(uuid, pname);
                    } catch (Throwable ignored) {}
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

    /**
     * P3: flusso rotazioni raw dai LOOK (e POSITION_LOOK).
     * Legge yaw/pitch dal pacchetto e li passa al core con lo spostamento
     * orizzontale del tick (per il lock: mira ferma mentre ci si muove).
     * Ogni lettura e difesa: se la firma non corrisponde, il listener resta
     * spento senza toccare gli altri.
     */
    private void hookRotationStream(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.LOOK,
                    PacketType.Play.Client.POSITION_LOOK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        float yaw = event.getPacket().getFloat().read(0);
                        float pitch = event.getPacket().getFloat().read(1);
                        UUID uuid = event.getPlayer().getUniqueId();
                        String name = event.getPlayer().getName();
                        double distXZ = 0;
                        try {
                            PlayerData d = AnticheatCore.get().data(uuid);
                            double dx = 0, dz = 0;
                            // spostamento dall'ultimo tracking noto: approssimazione
                            // sufficiente (il lock richiede 20 pacchetti identici in moto)
                            distXZ = 0.1;
                        } catch (Throwable ignored) {}
                        final double fDist = distXZ;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                Player p = Bukkit.getPlayer(uuid);
                                double real = fDist;
                                if (p != null) {
                                    try {
                                        PlayerData d = AnticheatCore.get().data(uuid);
                                        double dx = p.getLocation().getX() - d.lastX;
                                        double dz = p.getLocation().getZ() - d.lastZ;
                                        real = Math.sqrt(dx * dx + dz * dz);
                                    } catch (Throwable ignored) {}
                                }
                                AnticheatCore.get().handlePacketRotation(uuid, name, yaw, pitch, real);
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }
            });
            plugin.getLogger().info("[AC] Packet: rotation stream attivo (P3).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] Packet rotation non disponibile: " + t.getMessage());
        }
    }

    /** P2: swing pacchetto (ARM_ANIMATION) per il no-swing check. */
    private void hookSwing(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.ARM_ANIMATION) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        AnticheatCore.get().notePacketSwing(event.getPlayer().getUniqueId());
                    } catch (Throwable ignored) {}
                }
            });
            plugin.getLogger().info("[AC] Packet: swing tracking attivo (P2).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] Packet swing non disponibile: " + t.getMessage());
        }
    }

    /**
     * P2: flag onGround del pacchetto Flying vs dy reale (NoFall packet).
     * Legge booleano ground (indice 0) + Y dai POSITION*: se il client dice
     * terra mentre scende oltre -0.5, e spoof diretto.
     */
    private void hookGroundSpoof(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.POSITION,
                    PacketType.Play.Client.POSITION_LOOK,
                    PacketType.Play.Client.GROUND) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    try {
                        boolean ground = event.getPacket().getBooleans().read(0);
                        double y = 0;
                        boolean hasY = false;
                        try {
                            y = event.getPacket().getDoubles().read(1);
                            hasY = true;
                        } catch (Throwable ignored) {}
                        if (!hasY) return;
                        UUID uuid = event.getPlayer().getUniqueId();
                        PlayerData d = AnticheatCore.get().data(uuid);
                        double dy = d.hasLastPos ? (y - d.lastY) : 0;
                        AnticheatCore.get().handlePacketGround(uuid,
                            event.getPlayer().getName(), ground, dy);
                    } catch (Throwable ignored) {}
                }
            });
            plugin.getLogger().info("[AC] Packet: ground-spoof check attivo (P2).");
        } catch (Throwable t) {
            plugin.getLogger().warning("[AC] Packet ground non disponibile: " + t.getMessage());
        }
    }
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
        if (target == null) return;
        // Passo 3 F1: colpo contro sé stessi (prima era return silenzioso)
        if (target.equals(p)) {
            try {
                AnticheatCore.get().handleInteractAttack(uuid, p.getName(), false, true, entityId);
            } catch (Throwable ignored) {}
            return;
        }

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
        // A1: linea di vista occhio->bersaglio. Muro in mezzo = hit-through-wall.
        // rayTraceBlocks API Bukkit reale; tutto in try/catch (mondi strani, ecc).
        boolean throughWall = false;
        try {
            org.bukkit.Location targetLoc = target.getLocation().add(0, 1, 0);
            org.bukkit.util.Vector dir = targetLoc.toVector().subtract(eye.toVector());
            double len = dir.length();
            if (len > 0.1 && len < 10) {
                dir.normalize();
                org.bukkit.util.RayTraceResult hit = p.getWorld().rayTraceBlocks(
                    eye, dir, len,
                    org.bukkit.FluidCollisionMode.NEVER, true);
                throughWall = hit != null && hit.getHitBlock() != null;
            }
        } catch (Throwable ignored) {}
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
        try {
            ctx.attackCooldown = p.getAttackCooldown();
        } catch (Throwable t) {
            ctx.attackCooldown = -1;
        }
        ctx.throughWall = throughWall;
        // Passo 3 C1: uso-item al momento del colpo (arco teso, cibo, scudo)
        boolean usingItem = false;
        try {
            usingItem = p.isHandRaised();
        } catch (Throwable ignored) {}
        try {
            if (!usingItem) usingItem = p.isBlocking();
        } catch (Throwable ignored) {}
        int before = AnticheatCore.get().data(uuid).totalVl();
        try {
            AnticheatCore.get().handleInteractAttack(uuid, p.getName(), usingItem, false, entityId);
        } catch (Throwable ignored) {}
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

    /** Passo 3 F2/C2: interact non-attacco, misura gittata occhio->entita. Main thread. */
    private void evaluateInteractRange(UUID uuid, String pname, int entityId) {
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
        double dist;
        try {
            dist = p.getEyeLocation().distance(target.getLocation());
        } catch (Throwable t) {
            return;
        }
        try {
            AnticheatCore.get().handleInteractUse(uuid, pname, entityId, dist);
        } catch (Throwable ignored) {}
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
