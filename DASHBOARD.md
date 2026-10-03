# Nexira dashboard

Web panel for staff. Read-only: players, sessions, alt accounts. No actions
yet (unban etc. come with the punishment manager).

## Start

1. In `config.yml`:
   ```yaml
   dashboard:
     enabled: true
     port: 25596
     bind: "127.0.0.1" # never 0.0.0.0 without a reverse proxy
   ```
2. Restart (or `ac reload`). Console prints the URL.
3. First start creates `plugins/AntiCheat/dashboard.token` and prints it.
   Keep it — it's the only login.
4. Open `http://127.0.0.1:25596` **on the machine running the server**,
   paste the token. The browser remembers it.

New token any time: `ac dashboard token` (console or in game).
Status: `ac dashboard`.

## What you see

- **Players**: live list with IP, client brand + mod version, ping, VL,
  playtime. Click a row for violations and same-IP accounts.
- **Sessions**: every closed session (connect, disconnect, playtime,
  brand, IP, version), sortable by clicking headers, searchable by
  name or IP. Last 300 kept, lost on restart until SQLite lands.
- **Player card actions**: warn/kick/tempban/ban with duration + reason,
  unban, full punishment history. Bans show a Ban ID screen in game
  (reason, date, expiry, appeal link from `appeal-url:`).

## Exposing it (hosting)

Default bind is localhost: unreachable from outside, on purpose.
To reach it remotely you have three options, easiest first:

1. **Firewall + IP whitelist**: bind `0.0.0.0`, open the port only for
   your home IP. Fine if your IP rarely changes.
2. **Reverse proxy (recommended)**: keep bind localhost, put Caddy/Nginx
   in front with HTTPS (`https://ac.yourdomain.tld` → `127.0.0.1:25596`).
   Minimal Caddyfile:
   ```
   ac.yourdomain.tld {
       reverse_proxy 127.0.0.1:25596
   }
   ```
3. **Cloudflare Tunnel**: no open ports at all, access via Cloudflare
   Access (Google login or email OTP). Safest, ~10 minutes to set up.

Security notes: token auth (constant-time compare), no-cache headers,
rate limiting on login comes with the next update. Every future action
(unban etc.) will write an audit log.
