package it.anticheat.paper.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.core.checks.ScaffoldCheck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;

public class WorldListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        org.bukkit.Location loc = p.getLocation();
        int before = AnticheatCore.get().data(p.getUniqueId()).totalVl();
        AnticheatCore.get().handleBlockPlace(p.getUniqueId(), p.getName(),
            p.isOnGround(), loc.getPitch(), loc.getX(), loc.getZ(), p.isSneaking(), loc.getYaw());
        // RotationPlace + FarPlace: guarda davvero il blocco che piazza?
        // (becca gli scaffold con rotazioni silenziose)
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            try {
                org.bukkit.block.Block against = e.getBlockAgainst();
                org.bukkit.Location eye = p.getEyeLocation();
                org.bukkit.Location center = against.getLocation().add(0.5, 0.5, 0.5);
                double dist = eye.distance(center);
                double angle = ScaffoldCheck.lookAngleDiff(loc.getYaw(), loc.getPitch(),
                    center.getX() - eye.getX(), center.getY() - eye.getY(), center.getZ() - eye.getZ());
                AnticheatCore.get().handlePlaceRotation(p.getUniqueId(), p.getName(), angle, dist);
            } catch (Throwable ignored) {}
        }
        debugPlace(p, before);
    }

    private void debugPlace(Player p, int beforeVl) {
        if (!AnticheatCore.get().isDebug(p.getUniqueId())) return;
        PlayerData d = AnticheatCore.get().data(p.getUniqueId());
        Bukkit.getLogger().info("[AC-DBG] place " + p.getName()
            + " ground=" + p.isOnGround() + " sneak=" + p.isSneaking()
            + " scafStreak=" + d.scaffoldStreak + " rotStreak=" + d.rotPlaceStreak
            + " farStreak=" + d.farPlaceStreak + " VL " + beforeVl + "->" + d.totalVl());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        float hardness = 1.0f;
        try {
            hardness = e.getBlock().getType().getHardness();
        } catch (Throwable t) {
            hardness = 1.0f;
        }
        boolean hard = hardness > 0.05f;
        boolean hasHaste;
        try {
            hasHaste = p.hasPotionEffect(org.bukkit.potion.PotionEffectType.HASTE);
        } catch (Throwable t) {
            hasHaste = false;
        }
        org.bukkit.block.Block b = e.getBlock();
        String key = p.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
        int before = AnticheatCore.get().data(p.getUniqueId()).totalVl();
        // mine-timing PRIMA (legge la mappa danni intatta), poi break (la consuma)
        AnticheatCore.get().handleMineTiming(p.getUniqueId(), p.getName(), hardness, key, hasHaste);
        AnticheatCore.get().handleBlockBreak(p.getUniqueId(), p.getName(), hard, key);
        if (AnticheatCore.get().isDebug(p.getUniqueId())) {
            PlayerData d = AnticheatCore.get().data(p.getUniqueId());
            Bukkit.getLogger().info("[AC-DBG] break " + p.getName()
                + " hard=" + hard + " brkStreak=" + d.fastBreakStreak
                + " durStreak=" + d.fastBreakDurStreak + " VL " + before + "->" + d.totalVl());
        }
        // XRay statistico: diamanti/detriti vs pietra scavata
        org.bukkit.Material t = e.getBlock().getType();
        boolean valuable = t == org.bukkit.Material.DIAMOND_ORE
            || t == org.bukkit.Material.DEEPSLATE_DIAMOND_ORE
            || t == org.bukkit.Material.ANCIENT_DEBRIS;
        boolean stone = !valuable && (t == org.bukkit.Material.STONE
            || t == org.bukkit.Material.DEEPSLATE
            || t == org.bukkit.Material.NETHERRACK
            || t == org.bukkit.Material.TUFF
            || t == org.bukkit.Material.ANDESITE
            || t == org.bukkit.Material.DIORITE
            || t == org.bukkit.Material.GRANITE
            || t == org.bukkit.Material.CALCITE
            || t == org.bukkit.Material.SMOOTH_BASALT
            || t == org.bukkit.Material.BASALT
            || t == org.bukkit.Material.BLACKSTONE
            || t == org.bukkit.Material.GRAVEL);
        if (valuable || stone) {
            AnticheatCore.get().handleXrayBreak(p.getUniqueId(), p.getName(), valuable, true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        org.bukkit.block.Block b = e.getBlock();
        AnticheatCore.get().handleBlockDamage(p.getUniqueId(), p.getName(),
            p.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent e) {
        if (e.getEntity() instanceof Player p) {
            AnticheatCore.get().handleTotemPop(p.getUniqueId(), p.getName());
        }
    }
}
