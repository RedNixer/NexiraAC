package it.anticheat.paper.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import it.anticheat.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerVelocityEvent;

public class CombatListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Entity target = e.getEntity();
        double dist;
        try {
            dist = p.getLocation().distance(target.getLocation());
        } catch (Exception ex) {
            return;
        }
        Check.FightContext ctx = new Check.FightContext();
        ctx.distance = dist;
        ctx.ax = p.getLocation().getX();
        ctx.ay = p.getLocation().getY();
        ctx.az = p.getLocation().getZ();
        // distanza occhio->bersaglio: se il mob e sbalzato in aria, questa resta
        // corretta mentre piedi-a-piedi si gonfia di blocchi verticali
        try {
            ctx.eyeDistance = p.getEyeLocation().distance(target.getLocation());
        } catch (Throwable t) {
            ctx.eyeDistance = 0;
        }
        ctx.dtSinceLastAttackMillis = -1; // calcolato dal core
        try { ctx.ping = p.getPing(); } catch (Throwable t) { ctx.ping = 0; }
        try { ctx.blocking = p.isBlocking(); } catch (Throwable t) { ctx.blocking = false; }
        // yaw diff semplificata: angolo tra direzione sguardo e direzione target
        try {
            double dx = target.getLocation().getX() - p.getLocation().getX();
            double dz = target.getLocation().getZ() - p.getLocation().getZ();
            double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
            double diff = Math.abs(targetYaw - p.getLocation().getYaw()) % 360;
            if (diff > 180) diff = 360 - diff;
            ctx.targetYawDiff = diff;
            ctx.attackerYaw = p.getLocation().getYaw();
            ctx.attackerPitch = p.getLocation().getPitch();
        } catch (Throwable t) {
            ctx.targetYawDiff = 0;
        }
        // Con ProtocolLib attivo il fight arriva dai pacchetti (misura precisa
        // occhio->hitbox): salta la versione a eventi per non contare doppio.
        // I click restano sempre a eventi (gli swing pacchetto non li tracciamo).
        if (!AnticheatCore.get().isPacketFightPrimary()) {
            AnticheatCore.get().handleFight(p.getUniqueId(), p.getName(), ctx);
        }
        // Click registrati su swing e colpi: la valutazione CPS/regolarita
        // vive nel core (gate combat per le regole ritmiche).
        AnticheatCore.get().handleClick(p.getUniqueId(), p.getName());
        if (AnticheatCore.get().isDebug(p.getUniqueId())) {
            PlayerData d = AnticheatCore.get().data(p.getUniqueId());
            Bukkit.getLogger().info("[AC-DBG] fight " + p.getName()
                + " vs " + target.getType()
                + " dist=" + String.format("%.2f", dist)
                + " eye=" + String.format("%.2f", ctx.eyeDistance)
                + " yawDiff=" + String.format("%.0f", ctx.targetYawDiff)
                + " blocking=" + p.isBlocking()
                + " VL=" + d.totalVl() + " " + d.violations);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent e) {
        // Bow-snap: tiro subito dopo uno scatto di mira (aimbot arco)
        if (e.getEntity() instanceof Player p) {
            AnticheatCore.get().handleBowShoot(p.getUniqueId(), p.getName());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        // qualsiasi danno sballa la fisica per ~1.5s (knockback, esplosioni, cadute):
        // niente controlli movimento in quella finestra (il NoFall resta attivo)
        AnticheatCore.get().exemptMove(p.getUniqueId(), 1500);
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        AnticheatCore.get().handleFallDamage(p.getUniqueId(), p.getName(),
            p.getFallDistance(), e.getFinalDamage());
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        // knockback/esplosioni/riptide: 2s di tregua dai controlli movimento
        AnticheatCore.get().exemptMove(e.getPlayer().getUniqueId(), 2000);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof EnderPearl pearl
                && pearl.getShooter() instanceof Player p) {
            AnticheatCore.get().exemptMove(p.getUniqueId(), 3000);
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        // Registra lo swing per il calcolo CPS. Il flag scatta SOLO in contesto
        // combat (core), quindi scavare/costruire non rischiano nulla.
        AnticheatCore.get().handleClick(e.getPlayer().getUniqueId(), e.getPlayer().getName());
        if (AnticheatCore.get().isDebug(e.getPlayer().getUniqueId())) {
            PlayerData d = AnticheatCore.get().data(e.getPlayer().getUniqueId());
            long now = System.currentTimeMillis();
            int cps = 0;
            for (long ts : d.clickTimes) if (now - ts <= 1000) cps++;
            Bukkit.getLogger().info("[AC-DBG] click " + e.getPlayer().getName()
                + " cps(1s)=" + cps + " limite=" + AnticheatCore.get().config().cpsLimit
                + " VL=" + d.totalVl());
        }
    }
}
