package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * PacketOrder Fase 2: sequenza e coerenza dei pacchetti combat/movimento.
 * Il bridge alimenta i timestamp, qui solo valutazione (puro Java).
 *
 * A) Ordine: un ATTACK senza Flying/movimento nei 1000ms prima e impossibile
 *    (il client manda sempre posizione prima del colpo). Killaura che inietta
 *    pacchetti d'attacco fuori sequenza.
 * B) No-swing pacchetto: ATTACK senza ARM_ANIMATION nei 1000ms prima, x4
 *    (PacketMine/killaura silenziosa che non swinga mai).
 * C) Ground-spoof pacchetto: pacchetto GROUND=true mentre la Y scende oltre
 *    -0.5 nello stesso movimento, x5 (NoFall packet diretto).
 */
public class PacketOrderCheck extends Check {
    @Override public String name() { return "PacketOrder"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Sequenza pacchetti impossibile (solo Paper+PL)"; }

    /** Flying/movimento ricevuto: apre la finestra per attacchi legittimi. */
    public int onFlying(PlayerData data) {
        data.pktLastFlying = System.currentTimeMillis();
        return 0;
    }

    /** Swing (ARM_ANIMATION) ricevuto. */
    public void onSwing(PlayerData data) {
        data.pktLastSwing = System.currentTimeMillis();
    }

    /** ATTACK via pacchetto: valuta ordine + swing. */
    public int onPacketAttack(PlayerData data) {
        long now = System.currentTimeMillis();
        int out = 0;
        // A) nessun movimento prima del colpo: pacchetto iniettato
        if (data.pktLastFlying == 0 || now - data.pktLastFlying > 1000) {
            data.pktOrderStreak++;
            if (data.pktOrderStreak >= 3) {
                data.pktOrderStreak = 0;
                out = Math.max(out, 5);
            }
        } else {
            data.pktOrderStreak = 0;
        }
        // B) nessuno swing prima del colpo: killaura silenziosa
        if (data.pktLastSwing == 0 || now - data.pktLastSwing > 1000) {
            data.pktNoSwingStreak++;
            if (data.pktNoSwingStreak >= 4) {
                data.pktNoSwingStreak = 0;
                out = Math.max(out, 4);
            }
        } else {
            data.pktNoSwingStreak = 0;
        }
        return out;
    }

    /** Pacchetto GROUND=true con Y che scende: NoFall packet. */
    public int onPacketGround(PlayerData data, boolean packetGround, double dy) {
        if (packetGround && dy < -0.5) {
            data.pktGroundStreak++;
            if (data.pktGroundStreak >= 5) {
                data.pktGroundStreak = 0;
                return 5;
            }
        } else {
            data.pktGroundStreak = Math.max(0, data.pktGroundStreak - 1);
        }
        return 0;
    }
}
