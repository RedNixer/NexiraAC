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

## Checks (20)

Movement: Speed (spikes, metronome, hop rhythm), Fly (flight, airtime, stationary hover),
NoFall (ground spoof, cancelled damage, missing support), Step, Sprint (hunger, blindness,
item use), NoSlow (items, webs, soul sand), Timer, Jesus, Spider*.
Combat: Reach (ping bands, 6-hit window), KillAura (rate, yaw snap), AutoClicker
(your CPS limit, regularity), Multitask*, AimSnap (rotation snaps), AimLock (frozen aim,
bow snap).
World: Scaffold (rate, gap, sneak, yaw snap, rotation/face, range), FastBreak (damage
duration, intervals, no-swing, mine timing, same-tick multi-break, hotbar swap),
AutoTotem (rate, inventory-swap timing), XRay (statistical, staff alerts only).

\* experimental checks, off by default (`experimental-checks: true` to enable).

With ProtocolLib installed (Paper only, optional), two extra packet-level checks unlock:
precise Flying rate (Timer) and eye-to-hitbox attack range. Without it, everything else
keeps working on events alone.

## Nexira vs Grim vs Vulcan

| | Nexira | Grim | Vulcan |
|---|---|---|---|
| Price | Free, open source | Free, open source | Paid |
| Platforms | Paper/Purpur **and** Fabric, shared core | Bukkit and Fabric | Spigot/Paper forks |
| Checks | 20 event-based + 2 packet-level | 130+ packet-level with full movement simulation | 100+, strong combat reputation |
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

`/ac gui|settings|vl|reset|stats|debug|reports|cps|testmode|experimental|download|reload|add`
— most also work from console without the slash (`ac vl Steve`). Players report with
`/report <player> <reason>`.

Permissions: `anticheat.admin` (default op), `anticheat.exempt` (bypass all checks),
`anticheat.report` (default true).

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

- ProtocolLib phase 2/3: packet order, bad-packet sequences, raw rotation stream
- Tool-aware mine timing, inventory-action correlation on Fabric
- Web dashboard for violations history

## License

Copyright (c) 2026 RedNixer. Licensed under AGPL-3.0 (see LICENSE).
Techniques are inspired by Grim and Meteor; all code here is original.
