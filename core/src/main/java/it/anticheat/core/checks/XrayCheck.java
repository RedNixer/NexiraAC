package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * XRay statistico (rilevazione, non occultamento dei minerali).
 * Conta diamanti/antichi detriti vs pietra scavata: chi mina con l'XRay
 * trova diamanti a un ritmo impossibile per uno scavo legittimo.
 * Non banna mai da solo: VL bassa + avviso allo staff che verifica.
 */
public class XrayCheck extends Check {
    private int minStone = 500;
    private int minOres = 10;
    private double ratio = 0.03;
    private long cooldownMs = 30L * 60 * 1000;

    /** Soglie da config (il core le passa a ogni uso). */
    public void configure(int minStone, int minOres, double ratio, int cooldownMin) {
        this.minStone = minStone;
        this.minOres = minOres;
        this.ratio = ratio;
        this.cooldownMs = Math.max(1, cooldownMin) * 60L * 1000;
    }

    @Override public String name() { return "XRay"; }
    @Override public CheckType type() { return CheckType.WORLD; }
    @Override public String description() { return "Diamanti a ritmo impossibile (statistico)"; }

    /** Ritorna il testo dell'alert oppure null. */
    public String onBreak(PlayerData data, boolean valuable, boolean stone) {
        if (valuable) data.xrayOres++;
        else if (stone) data.xrayStone++;
        else return null;

        if (data.xrayStone >= minStone && data.xrayOres >= minOres) {
            double r = (double) data.xrayOres / data.xrayStone;
            long now = System.currentTimeMillis();
            if (r > ratio && now - data.xrayLastAlert > cooldownMs) {
                String alert = "diamanti=" + data.xrayOres + " su pietra=" + data.xrayStone
                    + " (" + String.format("%.1f", r * 100) + "%)";
                data.xrayLastAlert = now;
                data.xrayStone = 0;
                data.xrayOres = 0;
                return alert;
            }
        }
        // decadimento: campioni vecchissimi non devono pesare per sempre
        if (data.xrayStone > 4000) {
            data.xrayStone /= 2;
            data.xrayOres /= 2;
        }
        return null;
    }
}
