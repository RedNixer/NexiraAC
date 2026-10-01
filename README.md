<p align="center">
  <img src="assets/logo.png" alt="Nexira" width="320">
</p>

<h1 align="center">NEXIRA</h1>

<p align="center">
  <b>Beta</b> — under active development. It won't catch everything yet, and
  thresholds may need tuning on your server. Built and tested on 1.21.11;
  26.2 and 26.3 support coming soon.
</p>

<p align="center">
  Server-side anticheat for Minecraft 1.21.11 — Paper/Purpur and Fabric, one shared core.
  <br>
  <img src="https://img.shields.io/badge/minecraft-1.21.11-green" alt="mc 1.21.11">
  <img src="https://img.shields.io/badge/paper%20%2B%20fabric-blue" alt="paper + fabric">
  <img src="https://img.shields.io/badge/java-21-orange" alt="java 21">
</p>

Nexira watches movement, combat and world interaction from the server side and scores
every suspicion with violation levels (VL). Warn, kick, ban — or just teleport the
cheater back to solid ground with setback. Everything is tunable live from an in-game
admin panel, no restarts.

There is also an optional client companion mod: players who install it get a verified
badge, and staff get an F8 shortcut to the panel. The server never trusts the client —
the mod is only a signal, all decisions stay server-side.

## Checks (24)

Movement: Speed (spikes, metronome, hop rhythm), Fly (flight, airtime, stationary hover),
NoFall (ground spoof, cancelled damage, missing support), Step, Sprint (hunger, blindness,
item use), NoSlow (items, webs, soul sand), Timer, Jesus, Spider*, Prediction (vanilla
physics simulation: horizontal/vertical/hover against computed limits instead of fixed
thresholds — catches slow strafe and glides the old checks missed).
Combat: Reach (eye-to-hitbox, 3.05 + half ping margin, 8-hit window, through-wall raytrace),
KillAura (rate, yaw snap, 1.9+ attack-cooldown ignored), AutoClicker (your CPS limit,
regularity), Multitask*, AimSnap (rotation snaps), AimLock (frozen aim, bow snap),
RotationStream (raw LOOK packets: single-packet snaps, modulo-360 injections, duplicate
packets), PacketOrder (attack without movement/swing, packet ground-spoof), Interact
(mid-use attacks, self-hits, multi-entity ticks, long-range interacts).
World: Scaffold (rate, gap, sneak, yaw snap, rotation/face, range), FastBreak (damage
duration, intervals, no-swing, mine timing, same-tick multi-break, hotbar swap, vanilla
DPS per tool/enchants), AutoTotem (rate, inventory-swap timing, 300ms post-pop refill),
XRay (statistical, staff alerts only).

\* experimental checks, off by default (`experimental-checks: true` to enable).

No more "invisible at high ping": every check now scales tolerance continuously with
latency (longer streaks, wider margins) instead of switching off above 300ms. Knockback
is subtracted from observed movement instead of blanking all checks for 2 seconds.

With ProtocolLib installed (Paper only, optional), the packet-level checks unlock:
precise Flying rate, eye-to-hitbox range, attack order, raw rotation stream and
interact validation. Without it, everything else keeps working on events alone.
Note: on 1.21.11 you need a ProtocolLib dev-build (5.5.0+); the generic FLYING packet
type isn't registered there, so the timer listens on the four specific types.

## Nexira vs Grim vs Vulcan

| | Nexira | Grim | Vulcan |
|---|---|---|---|
| Price | Free, open source | Free, open source | Paid |
| Platforms | Paper/Purpur **and** Fabric, shared core | Bukkit and Fabric | Spigot/Paper forks |
| Checks | 24 (4 simulated/packet-level) | 130+ packet-level with full movement simulation | 100+, strong combat reputation |
| Performance | Light by default, packets opt-in | Heavier: simulates every player physics tick | Tuned for large networks |
| Setup | One config + live GUI tuning + test-mode that logs without punishing | Powerful but config-heavy, needs tuning experience | Close to plug-and-play |
| Client companion mod | Yes (verified badge, staff F8 panel) | No | No |
| Best for | Small/medium servers, Fabric servers, admins who want to see and tune everything | Large PvP networks facing advanced cheats | Large networks that want premium plug-and-play |

Honest note: Grim covers far more exotic cheats thanks to its prediction engine, and Vulcan
has years of tuning on big networks. Nexira trades coverage for simplicity: fewer moving
parts, readable code, every threshold adjustable in game. If you outgrow it, Grim is the
natural next step up.

## Requirements

- Java 21
- Paper/Purpur 1.21.11 **or** Fabric Loader 0.18.1 + Fabric API on 1.21.11
- Optional (Paper): ProtocolLib dev-build with 1.21.11 support for packet checks

## Install

1. Put `anticheat-paper-0.2.0.jar` in `plugins/` (Paper) or `fabric-mod-...+mc.1.21.11.jar`
   in `mods/` (Fabric server). Never both, never swapped — Paper jars don't go in `mods`.
2. Start the server once, then stop it.
3. Give yourself admin from the server console (no slash): `ac add YourName`
4. Start again. `/ac gui` in game opens the panel.

Client mod (optional): put `client-mod-...+mc.1.21.11.jar` (the one **without** `-dev`)
in the game `mods/` with Fabric API. Press F8 in game for the staff panel
(rebindable under Options > Controls).

## Commands

`/ac gui|settings|vl|reset|stats|debug|reports|cps|testmode|experimental|download|vanish|inv|ban|kick|unban|freeze|unfreeze|reload|add`
— most also work from console without the slash (`ac vl Steve`). Players report with
`/report <player> <reason>`. Punishments (per-check rules, durations, manual bans)
are explained in PUNISHMENTS.md.

Permissions: `anticheat.admin` (default op), `anticheat.exempt` (bypass all checks),
`anticheat.report` (default true).

## Staff mode (Paper)

`/ac vanish` turns you invisible to non-admins (admins still see you) while you keep
playing in survival. Right-click any player while vanished to open their inventory
live — what you take is really gone from them, no copy. `/ac inv <player>` opens the
same view from anywhere, no vanish needed.

## Config

One `config.yml`, same keys on Paper (`plugins/AntiCheat/`) and Fabric (`config/anticheat/`):
admin UUIDs, warn/kick/ban thresholds, per-check on/off, CPS limit, XRay profile,
setback, test-mode, experimental and packet toggles, client-mod policy. Everything is
also editable live from `/ac settings`.

## Build from source

```bat
.\gradlew.bat build
```

Jars land in each module's `build/libs/`. Needs internet on first run (Minecraft +
Fabric mappings download, ~1 GB). Java 21 required.

## Roadmap

Done recently: vanilla movement simulation, latency-as-uncertainty (no more
ping cutoffs), knockback subtraction, tool-aware mine DPS, packet order +
rotation stream, PvP pass (reach raytrace, attack cooldown, interact checks),
AutoTotem post-pop refill, staff vanish with live inventory inspect, Fabric
jar-in-jar fix (core ships inside the mod jar now).

Next:
- Fabric parity: 20-tick sampling, mine DPS and inventory clicks on Fabric
- Punishments per-check (N:M instead of global thresholds)
- SQLite/MySQL storage for violations history
- Web dashboard

## License

Copyright (c) 2026 RedNixer. Licensed under AGPL-3.0 (see LICENSE).
Techniques are inspired by Grim and Meteor; all code here is original.
