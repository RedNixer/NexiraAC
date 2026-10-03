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

/** ProtocolLib hooks (Paper only, optional). One listener per check, each isolated. */
public class PacketBridge {
    private final AnticheatPaper plugin;
    /** Errori lettura USE_ENTITY: se il mapping PL e rotto, il fallback eventi copre. */
    private volatile long useEntityErrors = 0;
    private volatile long lastUseEntityWarn = 0;

    private void warnUseEntity() {
        long now = System.currentTimeMillis();
        if (now - lastUseEntityWarn < 30000) return;
        lastUseEntityWarn = now;
        plugin.getLogger().warning("[AC] Packet USE_ENTITY illeggibile x" + useEntityErrors
            + " su questa build PL: fight via eventi (fallback attivo).");
    }

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
            // generic FLYING isn't registered on 1.21.11; the 4 specific types cover it
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
                        // API diretta PL (getAction sul wrapper): niente reflection.
                        // Se il mapping di questa build e rotto, conta e urla.
                        WrappedEnumEntityUseAction wrapped =
                            event.getPacket().getEnumEntityUseActions().read(0);
                        if (wrapped == null) {
                            useEntityErrors++;
                            warnUseEntity();
                            return;
                        }
                        isAttack = wrapped.getAction()
                            == com.comphenix.protocol.wrappers.EnumWrappers.EntityUseAction.ATTACK;
                    } catch (Throwable t) {
                        useEntityErrors++;
                        warnUseEntity();
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
                        // non-attack interacts go to range validation
                        Bukkit.getScheduler().runTask(plugin,
                            () -> evaluateInteractRange(uuid, pname, entityId));
                        return;
                    }
                    // packet-thread order check first, Bukkit reads on main
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

    /** Raw yaw/pitch from LOOK packets. Main thread for real distance. */
    private volatile boolean rotationBroken = false;
    private volatile int rotationMismatch = 0;

    private void hookRotationStream(ProtocolManager protocol) {
        try {
            protocol.addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL,
                    PacketType.Play.Client.LOOK) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    if (rotationBroken) return;
                    try {
                        float yaw = event.getPacket().getFloat().read(0);
                        float pitch = event.getPacket().getFloat().read(1);
                        // malformed floats (wrong signature on some builds): skip, don't flag
                        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) return;
                        if (Math.abs(yaw) > 360 || Math.abs(pitch) > 180) return;
                        UUID uuid = event.getPlayer().getUniqueId();
                        String name = event.getPlayer().getName();
                        // distXZ reale dall'ultimo move (non 0.1 fisso):
                        // il lock richiede moto vero, da fermo vale il branch duplicati.
                        double distXZ = 0;
                        try {
                            distXZ = AnticheatCore.get().data(uuid).lastMoveDistXZ;
                        } catch (Throwable ignored) {}
                        final double fDist = distXZ;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                Player p = Bukkit.getPlayer(uuid);
                                // real = ultimo move (fDist): d.lastX e gia aggiornato
                                // da handleMove, ricalcolarlo qui dava sempre ~0.
                                double real = fDist;
                                if (p != null) {
                                    // mapping self-test: raw must track Bukkit.
                                    // 20 straight mismatches = this PL build maps
                                    // the floats elsewhere -> kill the hook loudly.
                                    try {
                                        float by = p.getLocation().getYaw();
                                        float bp = p.getLocation().getPitch();
                                        double yd = Math.abs(by - yaw);
                                        if (yd > 180) yd = 360 - yd;
                                        double pd = Math.abs(bp - pitch);
                                        if (yd > 45 || pd > 45) {
                                            rotationMismatch++;
                                            if (rotationMismatch >= 20) {
                                                rotationBroken = true;
                                                plugin.getLogger().warning("[AC] Rotation stream disabled: "
                                                    + "float mapping mismatch on this ProtocolLib build.");
                                            }
                                            return;
                                        } else {
                                            rotationMismatch = 0;
                                        }
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

    /** ARM_ANIMATION swing feed for the no-swing check. */
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

    /** Packet onGround flag vs real dy: direct NoFall evidence. */
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
        // self-hits used to fall through silently
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
        ctx.targetId = entityId;
        try { ctx.targetIsPlayer = target instanceof Player; } catch (Throwable t) { ctx.targetIsPlayer = false; }
        ctx.dtSinceLastAttackMillis = -1; // calcolato dal core
        // eye-to-target blocked by a solid = wall hit
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
        // raised hand at hit time (bow drawn, food, shield)
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
        // Niente handleClick qui: 1 click = 1 swing Bukkit (onSwing).
        // Contarlo anche dal pacchetto doppiava il CPS come faceva onHit.
        if (AnticheatCore.get().isDebug(uuid)) {
            PlayerData d = AnticheatCore.get().data(uuid);
            Bukkit.getLogger().info("[AC-DBG] pkt-fight " + p.getName()
                + " vs " + target.getType()
                + " eyeBox=" + String.format("%.2f", eyeDist)
                + " VL " + before + "->" + d.totalVl() + " " + d.violations);
        }
    }

    /** Non-attack interacts: eye-to-entity range. Main thread. */
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
