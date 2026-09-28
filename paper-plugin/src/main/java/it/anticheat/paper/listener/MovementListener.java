package it.anticheat.paper.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getAllowFlight() || p.isFlying()) return; // creativa / fly permessa
        if (e.getTo() == null) return;
        double dx = e.getTo().getX() - e.getFrom().getX();
        double dy = e.getTo().getY() - e.getFrom().getY();
        double dz = e.getTo().getZ() - e.getFrom().getZ();
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        if (distXZ < 0.0001 && Math.abs(dy) < 0.0001) return;

        Check.MoveContext ctx = new Check.MoveContext();
        ctx.dx = dx; ctx.dy = dy; ctx.dz = dz;
        ctx.distXZ = distXZ;
        ctx.y = e.getTo().getY();
        ctx.yaw = e.getTo().getYaw();
        ctx.pitch = e.getTo().getPitch();
        ctx.onGround = p.isOnGround();
        ctx.flying = p.getAllowFlight() || p.isFlying();
        ctx.inWater = p.isInWater();
        ctx.onLadder = false;
        // supporto sotto i piedi (per NoFall anti-spoof): calcolato solo quando serve
        ctx.groundBelow = true;
        if (ctx.onGround && ctx.dy < -0.2 && !ctx.inWater && !ctx.flying) {
            try {
                org.bukkit.Location feet = p.getLocation();
                org.bukkit.block.Block b1 = feet.clone().subtract(0, 0.51, 0).getBlock();
                org.bukkit.block.Block b2 = feet.clone().subtract(0, 1.01, 0).getBlock();
                ctx.groundBelow = b1.getType().isSolid() || b2.getType().isSolid();
            } catch (Throwable t) {
                ctx.groundBelow = true;
            }
        }
        try { ctx.gliding = p.isGliding(); } catch (Throwable t) { ctx.gliding = false; }
        try { ctx.riding = p.isInsideVehicle(); } catch (Throwable t) { ctx.riding = false; }
        try { ctx.ping = p.getPing(); } catch (Throwable t) { ctx.ping = 0; }
        // stati per Sprint/NoSlow/Jesus (chiamate Bukkit economiche, senza blocchi)
        try { ctx.sprinting = p.isSprinting(); } catch (Throwable t) { ctx.sprinting = false; }
        try { ctx.blocking = p.isBlocking(); } catch (Throwable t) { ctx.blocking = false; }
        try { ctx.hungry = p.getFoodLevel() <= 6; } catch (Throwable t) { ctx.hungry = false; }
        try { ctx.blind = p.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS); } catch (Throwable t) { ctx.blind = false; }
        try { ctx.swimming = p.isSwimming(); } catch (Throwable t) { ctx.swimming = false; }
        // pozione Speed: alza i tetti legittimi (window scalati, metronomo spento)
        try {
            org.bukkit.potion.PotionEffect eff =
                p.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
            ctx.speedAmp = eff == null ? -1 : eff.getAmplifier();
        } catch (Throwable t) {
            ctx.speedAmp = -1;
        }
        // controlli su blocchi solo quando servono (getBlock costa)
        try {
            org.bukkit.Location feetLoc = p.getLocation();
            if (ctx.onGround && !ctx.flying) {
                org.bukkit.Material feetMat = feetLoc.getBlock().getType();
                org.bukkit.Material belowMat = feetLoc.clone().subtract(0, 0.51, 0).getBlock().getType();
                ctx.liquidFeet = feetMat == org.bukkit.Material.WATER || feetMat == org.bukkit.Material.LAVA;
                ctx.liquidBelow = belowMat == org.bukkit.Material.WATER || belowMat == org.bukkit.Material.LAVA;
                ctx.soulSand = feetMat == org.bukkit.Material.SOUL_SAND;
                if (ctx.soulSand) {
                    try {
                        org.bukkit.inventory.ItemStack boots = p.getInventory().getBoots();
                        ctx.soulSpeed = boots != null
                            && boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED) > 0;
                    } catch (Throwable t2) {
                        ctx.soulSpeed = false;
                    }
                }
            }
            if (!ctx.onGround && !ctx.flying && !ctx.gliding) {
                org.bukkit.Material feetMat = feetLoc.getBlock().getType();
                ctx.inCobweb = feetMat == org.bukkit.Material.COBWEB || feetMat == org.bukkit.Material.POWDER_SNOW;
                if (ctx.dy > 0.05) {
                    ctx.onLadder = feetMat == org.bukkit.Material.LADDER
                        || feetMat == org.bukkit.Material.VINE
                        || feetMat == org.bukkit.Material.WEEPING_VINES
                        || feetMat == org.bukkit.Material.TWISTING_VINES
                        || feetMat == org.bukkit.Material.CAVE_VINES
                        || feetMat == org.bukkit.Material.SCAFFOLDING;
                }
            }
        } catch (Throwable t) {
            ctx.liquidFeet = false;
            ctx.liquidBelow = false;
            ctx.inCobweb = false;
        }
        long now = System.currentTimeMillis();
        long last = AnticheatCore.get().data(p.getUniqueId()).lastMoveTime;
        ctx.dtMillis = last == 0 ? 50 : Math.min(1000, now - last);

        // teleport / lag spike: aggiorna il tracking ma non giudicare
        // (prima qui c'era solo return: il tracking restava fermo al pre-teleport
        // e ogni movimento dopo sembrava un delta gigante -> falsi positivi)
        if (distXZ > 12 || Math.abs(dy) > 12) {
            AnticheatCore.get().resetMoveState(p.getUniqueId(),
                e.getTo().getX(), e.getTo().getY(), e.getTo().getZ(),
                e.getTo().getWorld().getName());
            return;
        }

        AnticheatCore.get().handleMove(p.getUniqueId(), p.getName(), ctx,
            e.getTo().getX(), e.getTo().getY(), e.getTo().getZ(),
            e.getTo().getWorld().getName());
        if (AnticheatCore.get().isDebug(p.getUniqueId())) {
            it.anticheat.core.PlayerData d = AnticheatCore.get().data(p.getUniqueId());
            org.bukkit.Bukkit.getLogger().info("[AC-DBG] move " + p.getName()
                + " dy=" + String.format("%.2f", dy) + " ground=" + ctx.onGround
                + " below=" + ctx.groundBelow + " noGrStreak=" + d.noGroundStreak
                + " spoofStreak=" + d.groundSpoofStreak + " spdStreak=" + d.speedStreak
                + " VL=" + d.totalVl());
        }
    }
}
