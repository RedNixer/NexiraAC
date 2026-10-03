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

    private final org.bukkit.plugin.Plugin plugin;

    public WorldListener() {
        this.plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("AntiCheat");
    }

    public WorldListener(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

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
        // real tool + enchants + effects for the DPS check
        String tool = "HAND";
        int effLvl = 0;
        int hasteAmp = -1;
        int fatigueAmp = -1;
        try {
            org.bukkit.inventory.ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null) {
                String tn = hand.getType().name();
                if (tn.contains("WOODEN")) tool = "WOOD";
                else if (tn.contains("STONE")) tool = "STONE";
                else if (tn.contains("IRON")) tool = "IRON";
                else if (tn.contains("GOLDEN")) tool = "GOLD";
                else if (tn.contains("DIAMOND")) tool = "DIAMOND";
                else if (tn.contains("NETHERITE")) tool = "NETHERITE";
                try {
                    effLvl = hand.getEnchantmentLevel(
                        org.bukkit.enchantments.Enchantment.EFFICIENCY);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            org.bukkit.potion.PotionEffect h = p.getPotionEffect(
                org.bukkit.potion.PotionEffectType.HASTE);
            if (h != null) hasteAmp = h.getAmplifier();
        } catch (Throwable ignored) {}
        try {
            org.bukkit.potion.PotionEffect f = p.getPotionEffect(
                org.bukkit.potion.PotionEffectType.MINING_FATIGUE);
            if (f != null) fatigueAmp = f.getAmplifier();
        } catch (Throwable ignored) {}
        boolean inWater = false;
        try { inWater = p.isInWater(); } catch (Throwable ignored) {}
        boolean onGround = true;
        try { onGround = p.isOnGround(); } catch (Throwable ignored) {}
        // mine-timing first (reads the damage map), break consumes it
        AnticheatCore.get().handleMineTiming(p.getUniqueId(), p.getName(), hardness, key, hasHaste);
        AnticheatCore.get().handleMineDps(p.getUniqueId(), p.getName(), hardness, key,
            tool, effLvl, hasteAmp, fatigueAmp, inWater, onGround);
        AnticheatCore.get().handleBlockBreak(p.getUniqueId(), p.getName(), hard, key);
        if (AnticheatCore.get().isDebug(p.getUniqueId())) {
            PlayerData d = AnticheatCore.get().data(p.getUniqueId());
            Bukkit.getLogger().info("[AC-DBG] break " + p.getName()
                + " hard=" + hard + " brkStreak=" + d.fastBreakStreak
                + " durStreak=" + d.fastBreakDurStreak + " VL " + before + "->" + d.totalVl());
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
            // the pop eats the totem; one back 6 ticks later is scripted
            boolean hadMain = false;
            boolean hadOff = false;
            try {
                org.bukkit.inventory.ItemStack main = p.getInventory().getItemInMainHand();
                org.bukkit.inventory.ItemStack off = p.getInventory().getItemInOffHand();
                hadMain = main != null && main.getType() == org.bukkit.Material.TOTEM_OF_UNDYING;
                hadOff = off != null && off.getType() == org.bukkit.Material.TOTEM_OF_UNDYING;
            } catch (Throwable ignored) {}
            // snapshot hands; recheck in 6 ticks
            final java.util.UUID uuid = p.getUniqueId();
            final String name = p.getName();
            // double stock (both hands): the refill after is legit
            final boolean hadDouble = hadMain && hadOff;
            if (plugin != null) {
                try {
                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            Player pl = org.bukkit.Bukkit.getPlayer(uuid);
                            if (pl == null) return;
                            org.bukkit.inventory.ItemStack m2 = pl.getInventory().getItemInMainHand();
                            org.bukkit.inventory.ItemStack o2 = pl.getInventory().getItemInOffHand();
                            boolean hasNow = (m2 != null && m2.getType() == org.bukkit.Material.TOTEM_OF_UNDYING)
                                || (o2 != null && o2.getType() == org.bukkit.Material.TOTEM_OF_UNDYING);
                            // used totem is gone; one here with no double stock = refill
                            AnticheatCore.get().handleTotemRefill(uuid, name, hadDouble, hasNow);
                        } catch (Throwable ignored) {}
                    }, 6L);
                } catch (Throwable ignored) {}
            }
        }
    }
}
