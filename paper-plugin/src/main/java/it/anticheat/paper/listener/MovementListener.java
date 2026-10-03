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
        // creativa / fly permessa: niente check move, ma registra lo stato
        // (i bridge pacchetti girano anche qui e devono skippare)
        if (p.getAllowFlight() || p.isFlying()) {
            AnticheatCore.get().noteFlyState(p.getUniqueId());
            return;
        }
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
        // supporto sotto i piedi (per NoFall anti-spoof): calcolato solo quando serve.
        // Via mapper supports(): scale/slab/tappeti contano (isSolid li escludeva -> FP).
        ctx.groundBelow = true;
        if (ctx.onGround && ctx.dy < -0.2 && !ctx.inWater && !ctx.flying) {
            try {
                org.bukkit.Location feet = p.getLocation();
                it.anticheat.core.physics.BlockKind k1 = it.anticheat.paper.physics.PaperBlocks.kindOf(
                    feet.clone().subtract(0, 0.51, 0).getBlock().getType());
                it.anticheat.core.physics.BlockKind k2 = it.anticheat.paper.physics.PaperBlocks.kindOf(
                    feet.clone().subtract(0, 1.01, 0).getBlock().getType());
                ctx.groundBelow = k1.supports() || k2.supports();
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
        // mano alzata (arco/cibo/pozioni): Sprint e NoSlow erano ciechi su Paper
        // (usingItem lo riempiva solo Fabric). API Bukkit standard.
        try { ctx.usingItem = p.isHandRaised(); } catch (Throwable t) { ctx.usingItem = false; }
        // riptide: tridente a 15+ b/s, tregua rolling (come Fabric 2.5s ma precisa)
        try {
            if (p.isRiptiding()) AnticheatCore.get().exemptMove(p.getUniqueId(), 1000);
        } catch (Throwable t) { /* pre-1.13? mai qui, ignora */ }
        // slow falling / levitation via loop generico (slow_falling, levitation):
        // hover e salite vanilla, non cheat
        try {
            ctx.slowFall = false;
            for (org.bukkit.potion.PotionEffect eff : p.getActivePotionEffects()) {
                try {
                    String k = eff != null && eff.getType() != null && eff.getType().getKey() != null
                        ? eff.getType().getKey().getKey() : "";
                    if ("slow_falling".equals(k) || "levitation".equals(k)) {
                        ctx.slowFall = true;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            ctx.slowFall = false;
        }
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
        // attribute movement_speed (mod/effetti custom alzano oltre 0.1):
        // senza scala, ogni tetto sballa
        try {
            org.bukkit.attribute.AttributeInstance inst =
                p.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            ctx.moveSpeedAttr = inst == null ? 0.1 : inst.getValue();
        } catch (Throwable t) {
            ctx.moveSpeedAttr = 0.1;
        }
        // Jump Boost via loop effetti (nessun nome statico inventato):
        // il predictor lo usa per maxDyUp, senza sballa ogni salto potenziato
        try {
            ctx.jumpAmp = -1;
            for (org.bukkit.potion.PotionEffect eff : p.getActivePotionEffects()) {
                try {
                    if (eff != null && eff.getType() != null && eff.getType().getKey() != null
                            && "jump_boost".equals(eff.getType().getKey().getKey())) {
                        ctx.jumpAmp = eff.getAmplifier();
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            ctx.jumpAmp = -1;
        }
        // soffitto sopra la testa (salti troncati): serve in salita e in aria
        // (la finestra H da 120ms giudica sull'ultimo move, non sul picco).
        // Via mapper supports(): muro vero sopra, non foglie trasparenti.
        try {
            ctx.ceilingAbove = false;
            if ((dy > 0.05 || !ctx.onGround) && !ctx.flying && !ctx.gliding) {
                org.bukkit.Location head = p.getLocation();
                it.anticheat.core.physics.BlockKind c1 = it.anticheat.paper.physics.PaperBlocks.kindOf(
                    head.clone().add(0, 1.9, 0).getBlock().getType());
                it.anticheat.core.physics.BlockKind c2 = it.anticheat.paper.physics.PaperBlocks.kindOf(
                    head.clone().add(0, 2.4, 0).getBlock().getType());
                ctx.ceilingAbove = c1.supports() || c2.supports();
            }
        } catch (Throwable t) {
            ctx.ceilingAbove = false;
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
                // ghiaccio sotto (nome contiene ICE: ICE/PACKED/BLUE/FROSTED,
                // nessun enum inventato): scivolo 8-10 b/s legit
                try {
                    ctx.onIce = belowMat.name().contains("ICE");
                } catch (Throwable t2) {
                    ctx.onIce = false;
                }
                // atterraggio morbido (stesso check di onFall, gratis qui):
                // slime/honey/fieno/letti assorbono, mai pending NoFall
                try {
                    String bn = belowMat.name();
                    String fn = feetMat.name();
                    ctx.softLanding = bn.contains("SLIME") || bn.contains("HONEY")
                        || bn.contains("HAY") || bn.endsWith("_BED")
                        || fn.contains("COBWEB") || fn.contains("POWDER_SNOW");
                } catch (Throwable t2) {
                    ctx.softLanding = false;
                }
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
                // slime/honey sotto durante il bounce: softLanding anche in aria
                // (a terra lo calcola il blocco onGround sopra, qui il rimbalzo)
                if (!ctx.softLanding && ctx.dy > 0.5) {
                    try {
                        String bn = feetLoc.clone().subtract(0, 0.51, 0).getBlock().getType().name();
                        ctx.softLanding = bn.contains("SLIME") || bn.contains("HONEY") || bn.contains("HAY");
                    } catch (Throwable t2) {
                        ctx.softLanding = false;
                    }
                }
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
                + " predH=" + d.predHStreak + " predV=" + d.predVStreak
                + " winSpd=" + String.format("%.2f", d.lastPredWinSpeed)
                + " winMax=" + String.format("%.2f", d.lastPredWinMax)
                + " sprint=" + ctx.sprinting + " speedAmp=" + ctx.speedAmp
                + " ceil=" + ctx.ceilingAbove + " jump=" + ctx.jumpAmp + " ice=" + ctx.onIce
                + " attr=" + String.format("%.3f", ctx.moveSpeedAttr)
                + " ride=" + ctx.riding + " fly=" + ctx.flying + " glide=" + ctx.gliding
                + " dist=" + String.format("%.2f", distXZ) + " dt=" + ctx.dtMillis
                + " nfBr=" + d.lastNoFallBranch
                + " VL=" + d.totalVl());
            // Solver parallelo (Fase 2, mai VL): onGround calcolato vs dichiarato
            // + distanza tra simulato e reale. Se divergono spesso, il solver mente.
            try {
                org.bukkit.World w = e.getTo().getWorld();
                d.blockCache.fill(e.getFrom().getX(), e.getFrom().getY(), e.getFrom().getZ(),
                    (bx, by, bz) -> it.anticheat.paper.physics.PaperBlocks.kindOf(
                        w.getBlockAt(bx, by, bz).getType()));
                it.anticheat.core.physics.CollisionSolver.Result sr =
                    it.anticheat.core.physics.CollisionSolver.move(d.blockCache,
                        e.getFrom().getX(), e.getFrom().getY(), e.getFrom().getZ(), dx, dy, dz);
                double sx = sr.x - e.getTo().getX();
                double sy = sr.y - e.getTo().getY();
                double sz = sr.z - e.getTo().getZ();
                double off = Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (off > 0.3 || sr.onGround != ctx.onGround) {
                    org.bukkit.Bukkit.getLogger().info("[AC-SOL] " + p.getName()
                        + " off=" + String.format("%.2f", off)
                        + " calcGround=" + sr.onGround + " cliGround=" + ctx.onGround
                        + " step=" + sr.stepped + " hitXZ=" + (sr.hitX || sr.hitZ));
                }
            } catch (Throwable ignored) {}
        }
    }
}
